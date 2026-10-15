package src.bank_server;

import src.common.Log;
import src.common.Transaction;
import src.common.Timestamp;
import src.message_delivery_server.MessageDeliveryServerInterface;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * One copy (replica) of the bank account.
 *
 * deposit and addInterest are not done right away. We put them in
 * outstandingCollection and send them to the MessageDeliveryServer
 * every 10 seconds. The server sends them back to all replicas in
 * the same orderm so everyone ends up with the same balance.
 */
public class BankServer
        extends UnicastRemoteObject
        implements BankServerInterface {

    /*
     * =====================================================
     * FIELDS
     * =====================================================
     */

    // Local copy of the bank account
    private final Map<String, Double> balances = new HashMap<>();

    // Transactions created by this server but not yet executed
    private final List<Transaction> outstandingCollection = new ArrayList<>();

    // Transactions that have been ordered and executed
    private final List<Transaction> executedList = new ArrayList<>();

    // IDs of every transaction we have run. cleanHistory does not clear this,
    // se we can still skip duplicates and answer checkTxStatus.
    private final Set<String> executedIds = new HashSet<>();

    private int orderCounter = 0;
    private int outstandingCounter = 0;

    // Own counter for getSyncedBalance, so the normal
    // counter is only used for deposit and addInterest
    private int syncRequestCounter = 0;

    // Set with -Dsync=naive, default is correct
    static final boolean NAIVE_SYNC = "naive".equals(System.getProperty("sync", "correct"));

    // getSyncedBalance requests still waiting for answer (exit waits for them)
    private final List<CompletableFuture<?>> syncRequestsInProgress = new CopyOnWriteArrayList<>();

    // How long shutdown() waits for unanswered getSyncedBalance requests
    private static final int SYNC_WAIT_ON_EXIT_SECONDS = 30;

    private final String serverName;
    private final String accountName;
    private final int numberOfReplicas;

    // Remote reference to MessageDeliveryServer
    private MessageDeliveryServerInterface messageServer;

    // Member list from the server. We replace the whole list on
    // every change, so other threads never see half a list
    private volatile List<String> members = new ArrayList<>();

    // Own lock for member changes, so they do not have to wait for other work
    private final Object membershipLock = new Object();

    // Handles command parsing
    private final BankServerHelper helper;

    //getSyncedBalance requests, waiting for their marker to come back
    private final Map<String, CompletableFuture<Double>> pendingSyncResults = new ConcurrentHashMap<>();

    // Trading rates from the currency file (USD only until loaded)
    private CurrencyConverter converter = new CurrencyConverter();

    public BankServer(
            String serverName,
            String accountName,
            int numberOfReplicas) throws RemoteException {

        super();

        this.serverName = serverName;
        this.accountName = accountName;
        this.numberOfReplicas = numberOfReplicas;

        this.helper = new BankServerHelper(this);
    }

    /*
     * =====================================================
     * CONNECT TO MESSAGE DELIVERY SERVER
     * =====================================================
     */
    public void loadCurrencyRates(String path) throws java.io.IOException {
        converter = CurrencyConverter.fromFile(path);
    }

    public void connectToMessageServer(String host, int port) throws Exception {

        Registry registry = LocateRegistry.getRegistry(host, port);

        messageServer = (MessageDeliveryServerInterface) registry.lookup("MessageDeliveryServer");

        // Give MessageDeliveryServer a remote reference to this BankServer
        messageServer.join(accountName, this);

        System.out.println("[" + serverName + "] Connected to " + host + ":" + port);
    }

    /*
     * =====================================================
     * CREATE TRANSACTION
     * =====================================================
     *
     * Called by BankServerHelper for commands such as:
     *
     * deposit NOK 500
     * addInterest NOK 10
     */
    public synchronized void createTransaction(String command) {

        String uniqueId = serverName + " " + outstandingCounter;

        Transaction transaction = new Transaction(command, uniqueId);

        outstandingCollection.add(transaction);

        outstandingCounter++;

        System.out.println("[" + serverName + "] Transaction created: "
                        + command + " [" + uniqueId + "]");
    }

    /*
     * =====================================================
     * SEND OUTSTANDING TRANSACTIONS
     * =====================================================
     */
    public void broadcastOutstanding() {

        List<Transaction> transactions;

        synchronized (this) {

            if (outstandingCollection.isEmpty()) {
                return;
            }

            // Make a copy so the list can safely change later
            transactions = new ArrayList<>(outstandingCollection);
        }

        try {
            messageServer.broadcastTransactions(transactions);
        } catch (RemoteException e) {
            System.err.println("[" + serverName + "] Could not broadcast transaction: " + e.getMessage());
        }
    }

    /*
     * =====================================================
     * RECEIVE ORDERED TRANSACTION
     * =====================================================
     *
     * MessageDeliveryServer calls this through RMI.
     */
    @Override
    public synchronized void receiveTransaction(
            long messageId,
            List<Transaction> transactions) throws RemoteException {

        // Don't execute the same transaction twice
        for (Transaction transaction : transactions) {

            // A getSyncedBalance marker: only the replica that sent it answers.
            // Not a real transactionm so it is not put in the history.

            if (isSyncedBalanceRequest(transaction)) {
                resolveSyncedBalanceIfMine(transaction);
                outstandingCollection.removeIf(
                    tx -> tx.getUniqueId().equals(transaction.getUniqueId())
                );
                continue;
            }

            if (hasBeenExecuted(transaction.getUniqueId())) {
                System.out.println("[" + serverName + "] Duplicate ignored: " + transaction.getUniqueId());

                // Skip this one but keep going, we still have to send the ack
                continue;
            }

            System.out.println("[" + serverName + "] Received transaction: " + transaction.getCommand());

            // Actually modify the local account
            executeTransaction(transaction);
            transaction.setExecutedAt(Timestamp.now());

            // Transaction now has an order number
            orderCounter++;

            executedList.add(transaction);
            executedIds.add(transaction.getUniqueId());

            // If we originally created this transaction, then remove it from outstanding collection
            outstandingCollection.removeIf(
                    tx -> tx.getUniqueId().equals(transaction.getUniqueId())
                );
        }

        // Acknowledge to MessageDeliveryServer that we received the transaction
        messageServer.ack(serverName, messageId);
    }

    /*
     * =====================================================
     * STATE TRANSFER (late join)
     * =====================================================
     *
     * MessageDeliveryServer calls this through RMI to give
     * a newly joined replica the current state of the group.
     */
    @Override
    public synchronized void updateState(BankState state)
            throws RemoteException {

        System.out.println("[" + serverName + "] Received state update");

        balances.clear();
        balances.putAll(state.getBalances());

        executedList.clear();
        executedList.addAll(state.getExecutedList());

        executedIds.clear();

        if (state.getExecutedIds() != null) {
            executedIds.addAll(state.getExecutedIds());
        }
        for (Transaction transaction : executedList) {
            executedIds.add(transaction.getUniqueId());
        }

        orderCounter = state.getOrderCounter();

        System.out.println("[" + serverName + "] State copied: balances "
                        + balances + ", " + executedList.size()
                        + " executed transactions");
    }

    /*
     * Gives a copy of our state to the MessageDeliveryServer,
     * which passes it on to a replica that joins late.
     */
    @Override
    public synchronized BankState getState()
            throws RemoteException {

        return new BankState(
                new HashMap<>(balances),
                new ArrayList<>(executedList),
                orderCounter,
                new HashSet<>(executedIds));
    }

    /*
     * =====================================================
     * SERVER NAME
     * =====================================================
     *
     * MessageDeliveryServer calls this through RMI to
     * know who we are (member lists, ACK bookkeeping).
     */
    @Override
    public String getServerName() throws RemoteException {
        return serverName;
    }

    /*
     * =====================================================
     * MEMBERSHIP CHANGED
     * =====================================================
     *
     * MessageDeliveryServer calls this through RMI.
     */
    @Override
    public void membershipChanged(List<String> newMembers)
            throws RemoteException {

        synchronized (membershipLock) {

            members = new ArrayList<>(newMembers);

            // Wake up waitForInitialReplicas() so it re-checks the count
            membershipLock.notifyAll();
        }

        System.out.println("[" + serverName + "] Membership changed: "
                        + newMembers + " (" + newMembers.size() + " members)");
    }

    /*
     * =====================================================
     * WAIT FOR INITIAL REPLICAS
     * =====================================================
     *
     * Wait until all the first replicas have joined. A replica that
     * joins later already sees enough memebers and goes on at once
     */
    public void waitForInitialReplicas() throws InterruptedException {

        synchronized (membershipLock) {

            // We use "while" and not "if", since one join is not enough in all cases
            while (members.size() < numberOfReplicas) {

                System.out.println("[" + serverName + "] Waiting for replicas: "
                                + members.size() + "/" + numberOfReplicas);

                // wait() lets go of the lock while we wait
                membershipLock.wait();
            }
        }

        System.out.println("[" + serverName + "] All " + numberOfReplicas
                        + " initial replicas have joined, starting.");
    }

    /*
     * =====================================================
     * EXECUTE TRANSACTION
     * =====================================================
     */
    private void executeTransaction(Transaction transaction) {

        String command = transaction.getCommand();
        String[] parts = command.trim().split("\\s+");

        switch (parts[0]) {
            case "deposit": {
                String currency = parts[1];
                double amount = Double.parseDouble(parts[2]);
                applyDeposit(currency, amount);
                break;
            }

            case "addInterest": {
                // addInterest NOK 10
                if (parts.length == 3) {
                    String currency = parts[1];
                    double percent = Double.parseDouble(parts[2]);
                    applyInterest(currency, percent);
                }

                // addInterest 10
                else if (parts.length == 2) {
                    double percent = Double.parseDouble(parts[1]);
                    applyInterestToAll(percent);
                }
                break;
            }

            default:
                System.out.println("[" + serverName + "] Unknown transaction: " + command);
        }
    }

    /*
     * =====================================================
     * ACCOUNT OPERATIONS
     * =====================================================
     */
    private void applyDeposit(String currency, double amount) {

        double currentBalance = balances.getOrDefault(currency, 0.0);

        balances.put(currency, currentBalance + amount);

        System.out.println("[" + serverName + "] " + currency
                        + " balance: " + formatAmount(balances.get(currency)));
    }

    private void applyInterest(String currency, double percent) {

        double currentBalance = balances.getOrDefault(currency, 0.0);
        double newBalance = currentBalance * (1 + percent / 100.0);
        balances.put(currency, newBalance);
    }

    private void applyInterestToAll(double percent) {

        for (String currency : new ArrayList<>(balances.keySet())) {
            applyInterest(currency, percent);
        }
    }

    // Rounding only when we print. Stored value is not rounded
    private static String formatAmount(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    /*
     * =====================================================
     * QUICK BALANCE
     * =====================================================
     */
    public synchronized void getQuickBalance(
            String currency) {
        try {
            double balance = converter.convertTotal(balances, currency);

            System.out.println("[" + serverName + "] " + "Quick balance "
                            + currency + ": " + formatAmount(balance));

        } catch (IllegalArgumentException e) {
            System.out.println("[" + serverName + "] " + e.getMessage());
        }
    }

    /*
     * =====================================================
     * SYNCED BALANCE
     * =====================================================
     */
    public CompletableFuture<Double> getSyncedBalance(String currency) {

        String uniqueId;
        CompletableFuture<Double> future = new CompletableFuture<>();

        synchronized (this) {
            // "sync-" so it is never mixed up with a deposit or addInterest id
            uniqueId = serverName + " sync-" + syncRequestCounter;

            Transaction syncRequest = new Transaction("getSyncedBalance " + currency, uniqueId);
            outstandingCollection.add(syncRequest);
            syncRequestCounter++;
            pendingSyncResults.put(uniqueId, future);
        }

        System.out.println("[" + serverName + "] getSyncedBalance " + currency
                        + " queued [" + uniqueId + "], waiting for order...");

        // We do not wait here. The answer is printed when out marker comes back.
        // Commands after this one are ordered after the marker, so the do not count.
        CompletableFuture<Double> printed = future.whenComplete((balance, error) -> {
            if (error == null) {
                System.out.println("[" + serverName + "] Synced balance "
                                + currency + ": " + formatAmount(balance));
            } else {
                // e.g. a currency without a trading rate
                System.out.println("[" + serverName + "] " + error.getMessage());
            }
        });

        syncRequestsInProgress.add(printed);
        return printed;
    }

    // The naive version waits, so we run it in its own thread to keep the command loop going, same as the correct version
    public void startSyncedBalanceNaive(String currency) {

        System.out.println("[" + serverName + "] getSyncedBalance " + currency
                        + " (naive): waiting for an empty outstanding_collection...");

        CompletableFuture<Void> done = new CompletableFuture<>();

        Thread worker = new Thread(() -> {
            try {
                getSyncedBalanceNaive(currency);
            } finally {
                done.complete(null);
            }
        }, "naive-getSyncedBalance");

        // A stuck naive request must not keep the JVM alive
        worker.setDaemon(true);

        syncRequestsInProgress.add(done);
        worker.start();
    }

    public void getSyncedBalanceNaive(String currency) {

        while (true) {

            synchronized (this) {
                if (outstandingCollection.isEmpty()) {
                    try {
                        double balance = converter.convertTotal(balances, currency);

                        System.out.println("[" + serverName + "] Synced balance (naive) "
                                        + currency + ": " + formatAmount(balance));
                    } catch (IllegalArgumentException e) {
                        System.out.println("[" + serverName + "] " + e.getMessage());
                    }
                    return;
                }
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private boolean isSyncedBalanceRequest(Transaction transaction) {
        return transaction.getCommand().startsWith("getSyncedBalance");
    }

    private void resolveSyncedBalanceIfMine(Transaction transaction) {
        // Only the replica that sent the request finds it here
        CompletableFuture<Double> future = pendingSyncResults.remove(transaction.getUniqueId());

        if (future == null) {
            return;
        }

        String[] parts = transaction.getCommand().trim().split("\\s+");
        String currency = parts[1];

        try {
            future.complete(converter.convertTotal(balances, currency));
        } catch (IllegalArgumentException e) {
            future.completeExceptionally(e);
        }
    }

    /*
     * =====================================================
     * HISTORY
     * =====================================================
     */
    public synchronized void getHistory() {

        // cleanHistory never resets orderCounter, so the numbers keep going
        int order = orderCounter - executedList.size();

        System.out.println("[" + serverName + "] executed_list");

        for (Transaction transaction : executedList) {
            System.out.println(order + ". "
                            + transaction.getExecutedAt() + " "
                            + transaction.getCommand()
                            + "  [" + transaction.getUniqueId() + "]");
            order++;
        }

        // Outstanding transactions have no order number yet: they only
        // get one when the totally ordered broadcast comes back.
        System.out.println("[" + serverName + "] outstanding_collection");

        for (Transaction transaction : outstandingCollection) {

            // Skip getSyncedBalance markers
            if (isSyncedBalanceRequest(transaction)) {
                continue;
            }

            System.out.println("[" + serverName + "] " + transaction.getCommand() + "  [" + transaction.getUniqueId() + "]");
        }
    }

    /*
     * =====================================================
     * CHECK TRANSACTION STATUS
     * =====================================================
     */
    public synchronized void checkTxStatus(String id) {

        // Just a number means one of our own transactions, so we add name to make the ID unique.
        String uniqueId = id.matches("\\d+") ? serverName + " " + id : id;

        if (hasBeenExecuted(uniqueId)) {

            System.out.println(uniqueId + ": EXECUTED");

            return;
        }

        for (Transaction transaction : outstandingCollection) {

            if (transaction.getUniqueId().equals(uniqueId)) {
                System.out.println(uniqueId + ": OUTSTANDING");
                return;
            }
        }

        System.out.println(uniqueId + ": UNKNOWN");
    }

    /*
     * =====================================================
     * CHECK IF TRANSACTION WAS ALREADY EXECUTED
     * =====================================================
     *
     * Uses executedIds (never cleared) instead of
     * executedList (cleared by cleanHistory).
     */
    private boolean hasBeenExecuted(String uniqueId) {
        return executedIds.contains(uniqueId);
    }

    /*
     * =====================================================
     * CLEAN HISTORY
     * =====================================================
     */
    public synchronized void cleanHistory() {
        executedList.clear();
        System.out.println("[" + serverName + "] History cleaned.");
    }

    /*
     * =====================================================
     * MEMBER INFO
     * =====================================================
     */
    public void memberInfo() {

        // The list can be replaced while we print, so we keep our own reference
        List<String> current = members;

        System.out.println("[" + serverName + "] memberInfo: " + current + " (" + current.size() + " members)");
    }

    /*
     * =====================================================
     * SHUTDOWN (the "exit" command)
     * =====================================================
     */
    private void waitForSyncRequests() {

        for (CompletableFuture<?> request : syncRequestsInProgress) {
            try {
                request.get(SYNC_WAIT_ON_EXIT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                System.out.println("[" + serverName + "] getSyncedBalance still unanswered after " + SYNC_WAIT_ON_EXIT_SECONDS + " s, exiting anyway");
                return;
            } catch (ExecutionException e) {
                // the request already printed its own error message
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public void shutdown() {

        // 1. Send what we still have before we leave
        broadcastOutstanding();

        // Wait for getSyncedBalance answers that are not printed yet
        waitForSyncRequests();

        // 2. Leave the group, so the server removes us right away instead of marking us FAIL
        try {
            messageServer.leave(accountName, this);
        } catch (RemoteException e) {
            System.err.println("[" + serverName + "] Could not leave group: " + e.getMessage());
        }

        System.out.println("[" + serverName + "] exiting.");

        // 3. RMI keeps the program running, have to exit ourselves
        System.exit(0);
    }

    /*
     * =====================================================
     * COMMAND LINE INPUT
     * =====================================================
     */
    private void runCommandLine() {

        Scanner scanner = new Scanner(System.in);

        System.out.println("Reading commands from command line.");

        // Stop when the input ends (Ctrl+D)
        while (true) {

            System.out.print("[" + serverName + "] > ");

            // No newline after the prompt, flushing to show
            System.out.flush();

            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine();

            // User pressed Enter, so the next output line needs a new timestamp
            Log.promptAnswered();

            boolean keepRunning = helper.handleCommand(input);

            if (!keepRunning) {
                break;
            }
        }

        scanner.close();
    }

    /*
     * =====================================================
     * INPUT FILE
     * =====================================================
     */
    private void runCommandFile(String filename) throws Exception {

        Path path = Paths.get(filename);
        List<String> lines = Files.readAllLines(path);

        System.out.println("Reading commands from: " + filename);

        for (String line : lines) {

            if (line.isBlank()) {
                continue;
            }

            System.out.println("[" + serverName + "] > " + line);

            boolean keepRunning = helper.handleCommand(line);

            if (!keepRunning) {
                break;
            }

            // Random delay between 0.5 and 1.5 seconds.
            long delay = 500 + (long) (Math.random() * 1000);
            Thread.sleep(delay);
        }
    }

    // BROADCAST THREAD
    private void startBroadcastThread() {

        Thread broadcastThread = new Thread(() -> {

            while (true) {
                try {
                    Thread.sleep(10_000);
                    broadcastOutstanding();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        // The Daemon means the thread will not prevent the JVM from shutting down.
        broadcastThread.setDaemon(true);

        broadcastThread.start();
    }

    /*
     * =====================================================
     * MAIN
     * =====================================================
     */

    public static void main(
            String[] args) throws Exception {

        // Timestamp on every output line (this is first, so everything below is timestamped)
        Log.install();

        //Expected: java BankServer <server address> <account name> <number replicas> <currency file> [command file]
        if (args.length < 4) {
            System.out.println(
                    "Usage: java [-Dsync=correct|naive] -jar BankServer.jar "
                            + "<host:port> "
                            + "<account name> "
                            + "<number of replicas> "
                            + "<currency file> "
                            + "[command file]");

            System.exit(1);
        }

        // MessageDeliveryServer address
        String[] address = args[0].split(":");

        if (address.length != 2) {
            System.out.println("Server address must be host:port");
            System.exit(1);
        }

        String host = address[0];

        int port = Integer.parseInt(address[1]);

        String accountName = args[1];

        int numberOfReplicas = Integer.parseInt(args[2]);

        String currencyFile = args[3];

        /*
         * Which getSyncedBalance implementation to use:
         * java -Dsync=naive ... or java -Dsync=correct ...
         * Default is "correct".
         */
        String syncMode = System.getProperty("sync", "correct");

        if (!syncMode.equals("correct") && !syncMode.equals("naive")) {
            System.out.println("-Dsync must be 'correct' or 'naive', not '" + syncMode + "'");
            System.exit(1);
        }

        // PID + a random part, this ensure that two replicas do not get the same name,
        // this is true even on different machines like: "BankServer-42398-a3r3"
        String serverName = "BankServer-"
                + ProcessHandle.current().pid()
                + "-"
                + java.util.UUID.randomUUID().toString().substring(0, 4);


        BankServer server = new BankServer(serverName, accountName, numberOfReplicas);
        server.loadCurrencyRates(currencyFile);

        System.out.println("Starting " + serverName);

        System.out.println("Account: " + accountName);

        System.out.println("Expected replicas: " + numberOfReplicas);

        System.out.println("Currency file: " + currencyFile);

        System.out.println("getSyncedBalance mode: " + syncMode);

        server.connectToMessageServer(host, port);

        // wait for all replicas before starting sending, so noone misses a message
        server.waitForInitialReplicas();

        // Start 10 second broadcasting
        server.startBroadcastThread();

        if (args.length >= 5) {

            server.runCommandFile(
                    args[4]);

        } else {

            server.runCommandLine();
        }

        // We get here on exit, at the end of the file or when input ends
        server.shutdown();
    }
}
