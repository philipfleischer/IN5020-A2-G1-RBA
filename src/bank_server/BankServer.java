package src.bank_server;

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

    // uniqueIds of ALL transactions this replica has ever executed.
    // Unlike executedList this is never cleared by cleanHistory, so it is
    // what we use to guarantee "each transaction is executed only once"
    // and to answer checkTxStatus after the history has been cleaned.
    private final Set<String> executedIds = new HashSet<>();

    private int orderCounter = 0;
    private int outstandingCounter = 0;

    // Separate counter for getSyncedBalance requests. They also go through
    // outstandingCollection, but the assignment numbers only deposit and
    // addInterest with outstanding_counter, so "checkTxStatus 0" in the
    // input files must refer to the first deposit/addInterest.
    private int syncRequestCounter = 0;

    // Which getSyncedBalance implementation the command uses. Chosen at
    // start-up with -Dsync=naive (default: correct), so the same input
    // files can be run with both versions and the outputs compared.
    static final boolean NAIVE_SYNC = "naive".equals(System.getProperty("sync", "correct"));

    // getSyncedBalance requests that have not printed their answer yet.
    // shutdown() waits for them, so "getSyncedBalance" right before
    // "exit" still prints its answer.
    private final List<CompletableFuture<?>> syncRequestsInProgress = new CopyOnWriteArrayList<>();

    // How long shutdown() waits for unanswered getSyncedBalance requests
    private static final int SYNC_WAIT_ON_EXIT_SECONDS = 30;

    private final String serverName;
    private final String accountName;
    private final int numberOfReplicas;

    // Remote reference to MessageDeliveryServer
    private MessageDeliveryServerInterface messageServer;

    // Latest membership view from the MessageDeliveryServer. The list is
    // REPLACED (never modified) on every change, so a reader always sees
    // a complete list. volatile makes the new reference visible to all
    // threads at once.
    private volatile List<String> members = new ArrayList<>();

        // Used to wait for / signal membership changes. A separate lock,
        // so a membership update is never blocked by a
    // running receiveTransaction() or command.
    private final Object membershipLock = new Object();

    // Handles command parsing
    private final BankServerHelper helper;


// Pending getSyncedBalance requests this server originated, keyed by
// the transaction's uniqueId. receiveTransaction() completes these
// once the matching transaction comes back orderd.
        private final Map<String, CompletableFuture<Double>> pendingSyncResults =
                new ConcurrentHashMap<>();

// Trading rates from the currency file (USD only until loaded)
private CurrencyConverter converter = new CurrencyConverter();

    public BankServer(
            String serverName,
            String accountName,
            int numberOfReplicas
    ) throws RemoteException {

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

    public void connectToMessageServer(
            String host,
            int port
    ) throws Exception {

        Registry registry =
                LocateRegistry.getRegistry(host, port);

        messageServer =
                (MessageDeliveryServerInterface)
                        registry.lookup("MessageDeliveryServer");

        // Give MessageDeliveryServer a remote reference to this BankServer
        messageServer.join(accountName, this);

        System.out.println(
                "[" + serverName + "] Connected to "
                        + host + ":" + port
        );
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

        String uniqueId =
                serverName + " " + outstandingCounter;

        Transaction transaction =
                new Transaction(command, uniqueId);

        outstandingCollection.add(transaction);

        outstandingCounter++;

        System.out.println(
                "[" + serverName + "] Transaction created: "
                        + command
                        + " [" + uniqueId + "]"
        );
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
            transactions =
                    new ArrayList<>(outstandingCollection);
        }

        try {

            messageServer.broadcastTransactions(transactions);

        } catch (RemoteException e) {

            System.err.println(
                    "[" + serverName
                            + "] Could not broadcast transaction: "
                            + e.getMessage()
            );
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
            List<Transaction> transactions
    ) throws RemoteException {

        // Don't execute the same transaction twice
        for (Transaction transaction : transactions) {

          // getSyncedBalance is not a deposit/addInterest:it is executed
         // only by the replica that originated it, and it never joins
        // the executedlist (so it must never increment orderCounter either).
       // that keeps "orderCounter's executedList.size()" meaningful in
      // getHistory. Every other replica just discards it here."

        if (isSyncedBalanceRequest(transaction)){
            resolveSyncedBalanceIfMine(transaction);
            outstandingCollection.removeIf
(tx -> tx.getUniqueId().equals(transaction.getUniqueId())
);
continue;
}


            if (hasBeenExecuted(transaction.getUniqueId())) {
                System.out.println(
                        "[" + serverName
                                + "] Duplicate ignored: "
                                + transaction.getUniqueId()
                );

                // Skip only THIS transaction. The rest of the message may
                // contain new transactions, and we must still reach the
                // ack() below, otherwise the MessageDeliveryServer waits
                // for us forever (or marks us as FAIL).
                continue;
            }

            System.out.println(
                    "[" + serverName
                            + "] Received transaction: "
                            + transaction.getCommand()
            );

            // Actually modify the local account
            executeTransaction(transaction);
            transaction.setExecutedAt(Timestamp.now());

            // Transaction now has an order number
            orderCounter++;

            executedList.add(transaction);
            executedIds.add(transaction.getUniqueId());

            // If we originally created this transaction,
            // remove it from our outstanding collection
            outstandingCollection.removeIf(
                    tx -> tx.getUniqueId()
                            .equals(transaction.getUniqueId())
            );
        }

        // Acknowledge to MessageDeliveryServer that we received the transaction
        messageServer.ack(serverName, messageId);
    }

    /*
     * =====================================================
     * MEMBERSHIP CHANGED
     * =====================================================
     *
     * MessageDeliveryServer calls this through RMI.
     */
    @Override
    public synchronized void updateState(BankState state)
            throws RemoteException {

        System.out.println(
                "[" + serverName + "] Received state update"
        );

        // TODO: later we copy the received state
        // into this BankServer
    }

    /*
     * =====================================================
     * MEMBERSHIP CHANGED
     * =====================================================
     *
     * MessageDeliveryServer calls this through RMI.
     */
    @Override
    public String getServerName()
            throws RemoteException {

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

            System.out.println(
                            "[" + serverName + "] Membership changed: "
                                            + newMembers
                                            + " (" + newMembers.size() + " members)");
    }


    /*
     * =====================================================
     * WAIT FOR INITIAL REPLICAS
     * =====================================================
     *
     * Spec: "The bank server should wait until it detects that all
     * <number of replicas> bank servers have joined the group."
     *
     * A replica that joins later already sees >= numberOfReplicas
     * members and continues immediately.
     */
    public void waitForInitialReplicas() throws InterruptedException {

            synchronized (membershipLock) {

                    // "while", not "if": every join wakes us up (2/3 is still
                    // not enough), and Java allows spurious wake-ups.
                    while (members.size() < numberOfReplicas) {

                            System.out.println(
                                            "[" + serverName + "] Waiting for replicas: "
                                                            + members.size() + "/" + numberOfReplicas);

                            // wait() releases membershipLock while sleeping, so
                            // membershipChanged() can get in and call notifyAll().
                            membershipLock.wait();
                    }
            }

            System.out.println(
                            "[" + serverName + "] All " + numberOfReplicas
                                            + " initial replicas have joined, starting.");
    }

    /*
     * =====================================================
     * EXECUTE TRANSACTION
     * =====================================================
     */

    private void executeTransaction(Transaction transaction) {

        String command =
                transaction.getCommand();

        String[] parts =
                command.trim().split("\\s+");

        switch (parts[0]) {

            case "deposit": {

                String currency = parts[1];
                double amount =
                        Double.parseDouble(parts[2]);

                applyDeposit(currency, amount);

                break;
            }

            case "addInterest": {

                // addInterest NOK 10
                if (parts.length == 3) {

                    String currency = parts[1];

                    double percent =
                            Double.parseDouble(parts[2]);

                    applyInterest(
                            currency,
                            percent
                    );
                }

                // addInterest 10
                else if (parts.length == 2) {

                    double percent =
                            Double.parseDouble(parts[1]);

                    applyInterestToAll(percent);
                }

                break;
            }

            default:

                System.out.println(
                        "[" + serverName
                                + "] Unknown transaction: "
                                + command
                );
        }
    }


    /*
     * =====================================================
     * ACCOUNT OPERATIONS
     * =====================================================
     */

    private void applyDeposit(
            String currency,
            double amount
    ) {

        double currentBalance =
                balances.getOrDefault(currency, 0.0);

        balances.put(
                currency,
                currentBalance + amount
        );

        System.out.println(
                "[" + serverName + "] "
                        + currency
                        + " balance: "
                        + formatAmount(balances.get(currency))
        );
    }


    private void applyInterest(
            String currency,
            double percent
    ) {

        double currentBalance =
                balances.getOrDefault(currency, 0.0);

        double newBalance =
                currentBalance
                        * (1 + percent / 100.0);

        balances.put(
                currency,
                newBalance
        );
    }


    private void applyInterestToAll(double percent) {

            for (String currency : new ArrayList<>(balances.keySet())) {

                    applyInterest(currency, percent);
            }
    }


    // Balances are doubles, so 110 * 1.1 prints as 121.00000000000001.
    // We round only when PRINTING. The stored value keeps full precision,
    // so all replicas still compute exactly the same thing.
    private static String formatAmount(double amount) {
            return String.format(Locale.US, "%.2f", amount);
    }


    /*
     * =====================================================
     * QUICK BALANCE
     * =====================================================
     */

    public synchronized void getQuickBalance(
            String currency
    ) {
        try {
    double balance = converter.convertTotal(balances, currency);

    System.out.println(
            "[" + serverName + "] "
                    + "Quick balance "
                    + currency + ": "
                    + formatAmount(balance)
    );

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
            // "sync-" prefix: unique, and never mistaken for a
            // deposit/addInterest id such as "BankServer-1234 0"
            uniqueId = serverName + " sync-" + syncRequestCounter;

            Transaction syncRequest = new Transaction(
                    "getSyncedBalance " + currency,
                    uniqueId
            );
            outstandingCollection.add(syncRequest);
            syncRequestCounter++;
            pendingSyncResults.put(uniqueId, future);
        }

        System.out.println(
                "[" + serverName
                        + "] getSyncedBalance " + currency
                        + " queued [" + uniqueId + "], waiting for order..."
        );

        // We do NOT block here: the command loop goes on with the next
        // command, and the answer is printed when our marker comes back
        // ordered (resolveSyncedBalanceIfMine completes the future).
        // The marker is already in outstandingCollection, so commands
        // issued after this one are ordered AFTER it and cannot change
        // the answer.
        CompletableFuture<Double> printed =
                future.whenComplete((balance, error) -> {
                    if (error == null) {
                        System.out.println(
                                "[" + serverName + "] Synced balance "
                                        + currency + ": "
                                        + formatAmount(balance)
                        );
                    } else {
                        // e.g. a currency without a trading rate
                        System.out.println(
                                "[" + serverName + "] " + error.getMessage()
                        );
                    }
                });

        syncRequestsInProgress.add(printed);
        return printed;
    }

    /*
     * Runs the (blocking) naive version in its own thread, so the command
     * loop goes on exactly as in the correct version. Only the WAITING
     * strategy differs between the two, which makes them comparable.
     */
    public void startSyncedBalanceNaive(String currency) {

        System.out.println(
                "[" + serverName + "] getSyncedBalance " + currency
                        + " (naive): waiting for an empty outstanding_collection..."
        );

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
                    double balance =
                            converter.convertTotal(balances, currency);

                    System.out.println(
                            "[" + serverName + "] Synced balance (naive) "
                                    + currency + ": " + formatAmount(balance)
                    );
                } catch (IllegalArgumentException e) {
                    System.out.println(
                            "[" + serverName + "] " + e.getMessage()
                    );
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
   // Only the replica that created this request has a pending
   //  future for it - every other replica's map lookup is a no-op
   CompletableFuture<Double> future =
   pendingSyncResults.remove(transaction.getUniqueId());

   if (future == null) {
       return; // Not mine ignore
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

            // Spec: numbering starts at order_counter - executed_list.size.
            // cleanHistory empties the list but never resets order_counter,
            // so every entry keeps its global order number.
            int order = orderCounter - executedList.size();

            System.out.println("[" + serverName + "] executed_list");

            for (Transaction transaction : executedList) {
                    System.out.println(
                                    order + ". "
                                                    + transaction.getExecutedAt() + " "
                                                    + transaction.getCommand()
                                                    + "  [" + transaction.getUniqueId() + "]");
                    order++;
            }

            // Outstanding transactions have no order number yet: they only
            // get one when the totally ordered broadcast comes back.
            System.out.println("[" + serverName + "] outstanding_collection");

            for (Transaction transaction : outstandingCollection) {

                    // getSyncedBalance markers are not deposit/addInterest
                    // transactions, so they are not part of the history
                    if (isSyncedBalanceRequest(transaction)) {
                            continue;
                    }

                    System.out.println(
                                    transaction.getCommand()
                                                    + "  [" + transaction.getUniqueId() + "]");
            }
    }


    /*
     * =====================================================
     * CHECK TRANSACTION STATUS
     * =====================================================
     */

    public synchronized void checkTxStatus(
            String id
    ) {

        // The input files only give the counter ("checkTxStatus 0").
        // A bare number means "transaction number N created by THIS
        // server", so we add our own name to get the full unique id.
        String uniqueId =
                id.matches("\\d+")
                        ? serverName + " " + id
                        : id;

        if (hasBeenExecuted(uniqueId)) {

            System.out.println(
                    uniqueId + ": EXECUTED"
            );

            return;
        }


        for (Transaction transaction :
                outstandingCollection) {

            if (transaction.getUniqueId()
                    .equals(uniqueId)) {

                System.out.println(
                        uniqueId + ": OUTSTANDING"
                );

                return;
            }
        }


        System.out.println(
                uniqueId + ": UNKNOWN"
        );
    }


    /*
     * =====================================================
     * CHECK IF TRANSACTION WAS ALREADY EXECUTED
     * =====================================================
     *
     * Uses executedIds (never cleared) instead of executedList
     * (cleared by cleanHistory).
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

        // Important:
        // Do NOT reset orderCounter or outstandingCounter.

        System.out.println(
                "[" + serverName + "] History cleaned."
        );
    }


    /*
     * =====================================================
     * MEMBER INFO
     * =====================================================
     */

    public void memberInfo() {

            // Read the reference once: an RMI thread may replace "members"
            // while we print, but the list object we hold never changes.
            List<String> current = members;

            System.out.println(
                            "[" + serverName + "] memberInfo: "
                                            + current
                                            + " (" + current.size() + " members)");
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
                System.out.println(
                        "[" + serverName + "] getSyncedBalance still unanswered after "
                                + SYNC_WAIT_ON_EXIT_SECONDS + " s, exiting anyway"
                );
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

            // 1. Hand over transactions that were never broadcast, so the
            // other replicas still apply them after we are gone. The
            // message is queued BEFORE we leave, so it is not lost.
            broadcastOutstanding();

            // Wait for getSyncedBalance answers that are still on their
            // way. Our markers were just broadcast, so they come back
            // ordered; with no new input, the naive version's
            // outstanding_collection drains as well.
            waitForSyncRequests();

            // 2. Leave the group on purpose. The MessageDeliveryServer
            // removes us at once and sends a new member list to the
            // others, instead of waiting 5 s and marking us FAIL.
            try {
                    messageServer.leave(accountName, this);
            } catch (RemoteException e) {
                    System.err.println(
                                    "[" + serverName + "] Could not leave group: "
                                                    + e.getMessage());
            }

            System.out.println(
                            "[" + serverName + "] exiting.");

            // 3. The exported RMI object keeps non-daemon threads running,
            // so the JVM would never stop by itself. System.exit stops
            // it no matter which threads are still alive.
            System.exit(0);
    }


    /*
     * =====================================================
     * COMMAND LINE INPUT
     * =====================================================
     */

    private void runCommandLine() {

        Scanner scanner =
                new Scanner(System.in);

        System.out.println(
                "Reading commands from command line."
        );


        // hasNextLine() is false at end of input (Ctrl+D or a closed
        // pipe). nextLine() would throw NoSuchElementException there.
        while (true) {

                System.out.print(
                                "[" + serverName + "] > ");

                if (!scanner.hasNextLine()) {
                        break;
                }

                String input = scanner.nextLine();

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

    private void runCommandFile(
            String filename
    ) throws Exception {

        Path path =
                Paths.get(filename);

        List<String> lines =
                Files.readAllLines(path);

        System.out.println(
                "Reading commands from: "
                        + filename
        );


        for (String line : lines) {

            if (line.isBlank()) {
                continue;
            }

            System.out.println(
                    "[" + serverName + "] > "
                            + line
            );

            boolean keepRunning =
                    helper.handleCommand(line);

            if (!keepRunning) {
                break;
            }


            /*
             * Random delay between 0.5 and 1.5 seconds.
             */

            long delay =
                    500
                            + (long)
                            (Math.random() * 1000);

            Thread.sleep(delay);
        }
    }


    /*
     * =====================================================
     * BROADCAST THREAD
     * =====================================================
     */

    private void startBroadcastThread() {

        Thread broadcastThread =
                new Thread(() -> {

                    while (true) {

                        try {

                            Thread.sleep(10_000);

                            broadcastOutstanding();

                        } catch (InterruptedException e) {

                            Thread.currentThread()
                                    .interrupt();

                            break;
                        }
                    }
                });


        /*
         * Daemon means this thread won't prevent
         * the JVM from shutting down.
         */
        broadcastThread.setDaemon(true);

        broadcastThread.start();
    }


    /*
     * =====================================================
     * MAIN
     * =====================================================
     */

    public static void main(
            String[] args
    ) throws Exception {

        /*
         * Expected:
         *
         * java BankServer
         *      <server address>
         *      <account name>
         *      <number replicas>
         *      <currency file>
         *      [command file]
         */

        if (args.length < 4) {

            System.out.println(
                    "Usage: java BankServer "
                            + "<host:port> "
                            + "<account name> "
                            + "<number of replicas> "
                            + "<currency file> "
                            + "[command file]"
            );

            System.exit(1);
        }


        /*
         * ---------------------------------------------
         * MessageDeliveryServer address
         * ---------------------------------------------
         */

        String[] address =
                args[0].split(":");

        if (address.length != 2) {

            System.out.println(
                    "Server address must be host:port"
            );

            System.exit(1);
        }

        String host =
                address[0];

        int port =
                Integer.parseInt(address[1]);


        /*
         * ---------------------------------------------
         * Other arguments
         * ---------------------------------------------
         */

        String accountName =
                args[1];

        int numberOfReplicas =
                Integer.parseInt(args[2]);

        String currencyFile =
                args[3];


        /*
         * Which getSyncedBalance implementation to use:
         *   java -Dsync=naive ...   or   java -Dsync=correct ...
         * Default is "correct".
         */
        String syncMode =
                System.getProperty("sync", "correct");

        if (!syncMode.equals("correct") && !syncMode.equals("naive")) {

            System.out.println(
                    "-Dsync must be 'correct' or 'naive', not '"
                            + syncMode + "'"
            );

            System.exit(1);
        }


        /*
         * Give each process a unique name.
         *
         * PID is fine for now while developing.
         */
        String serverName =
                "BankServer-"
                        + ProcessHandle
                        .current()
                        .pid();


        /*
         * ---------------------------------------------
         * Create BankServer
         * ---------------------------------------------
         */

        BankServer server =
                new BankServer(
                        serverName,
                        accountName,
                        numberOfReplicas
                );
        server.loadCurrencyRates(currencyFile);


        System.out.println(
                "Starting " + serverName
        );

        System.out.println(
                "Account: " + accountName
        );

        System.out.println(
                "Expected replicas: "
                        + numberOfReplicas
        );

        System.out.println(
                "Currency file: "
                        + currencyFile
        );

        System.out.println(
                "getSyncedBalance mode: "
                        + syncMode
        );


        /*
         * ---------------------------------------------
         * Connect to MessageDeliveryServer
         * ---------------------------------------------
         */

        server.connectToMessageServer(
                        host,
                        port);

        /*
         * ---------------------------------------------
         * Wait until all <number of replicas> have joined.
         * Must happen BEFORE the broadcast thread and the
         * commands start, otherwise we could send
         * transactions that a not-yet-joined replica
         * never receives.
         * ---------------------------------------------
         */

        server.waitForInitialReplicas();

        /*
         * ---------------------------------------------
         * Start 10 second broadcasting
         * ---------------------------------------------
         */

        server.startBroadcastThread();


        /*
         * ---------------------------------------------
         * Start 10 second broadcasting
         * ---------------------------------------------
         */

        server.startBroadcastThread();


        /*
         * ---------------------------------------------
         * Input
         * ---------------------------------------------
         */

        if (args.length >= 5) {

            server.runCommandFile(
                    args[4]
            );

        } else {

            server.runCommandLine();
        }

        /*
         * Reached on "exit", at the end of the batch file,
         * or at end of input. Spec: batch mode must "exit".
         */
        server.shutdown();
    }
}
