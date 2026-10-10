package src.bank_server;

import src.common.Transaction;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ConcurrentHashMap;

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

    private final String serverName;
    private final String accountName;
    private final int numberOfReplicas;

    // Remote reference to MessageDeliveryServer
    private MessageDeliveryServerInterface messageServer;

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
    public void membershipChanged(List<String> members)
            throws RemoteException {
        System.out.println("Members: " + members);
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
                        + balances.get(currency)
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

        for (String currency :
                new ArrayList<>(balances.keySet())) {

            applyInterest(currency, percent);
        }
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
                    + balance
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

    public void getSyncedBalance(String currency) {

       String uniqueId;
       CompletableFuture<Double> future = new CompletableFuture<>();
       synchronized (this){
           uniqueId = serverName + " " + outstandingCounter;

           Transaction syncRequest = new Transaction("getSyncedBalance " + currency,
            uniqueId
);
          outstandingCollection.add(syncRequest);
          outstandingCounter++;
          pendingSyncResults.put(uniqueId, future);
       }

        System.out.println(
                "[" + serverName
                        + "] getSyncedBalance " + currency
                + " queued [" + uniqueId + "], waiting for order..."
        );

        try {
           // Blocks without holding the object lock, so receiveTransaction()
          // (also synchronized) stays free to deliver the ordered result.
                  double balance = future.get();
            System.out.println(
              "[" + serverName + "] Synced balance " + currency
              + ": " + balance
);
      } catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    System.err.println("[" + serverName + "] getSyncedBalance interrupted");
} catch (ExecutionException e) {
    // e.g. a currency without a trading rate
    System.out.println("[" + serverName + "] " + e.getCause().getMessage());
}
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
                                    + currency + ": " + balance
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

        System.out.println("Executed transactions:");

        int firstOrder =
                orderCounter - executedList.size();

        int order = firstOrder;

        for (Transaction transaction : executedList) {

            System.out.println(
                    order
                            + ": "
                            + transaction.getCommand()
                            + " ["
                            + transaction.getUniqueId()
                            + "]"
            );

            order++;
        }


        System.out.println("Outstanding transactions:");

        for (Transaction transaction :
                outstandingCollection) {

            System.out.println(
                    transaction.getCommand()
                            + " ["
                            + transaction.getUniqueId()
                            + "]"
            );
        }
    }


    /*
     * =====================================================
     * CHECK TRANSACTION STATUS
     * =====================================================
     */

    public synchronized void checkTxStatus(
            String uniqueId
    ) {

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

        /*
         * TODO:
         *
         * Later this should use membership information
         * from MessageDeliveryServer.
         */

        System.out.println(
                "[" + serverName + "]"
                        + " account = " + accountName
                        + ", expected replicas = "
                        + numberOfReplicas
        );
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


        while (true) {

            System.out.print(
                    "[" + serverName + "] > "
            );

            String input =
                    scanner.nextLine();

            boolean keepRunning =
                    helper.handleCommand(input);

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


        /*
         * ---------------------------------------------
         * Connect to MessageDeliveryServer
         * ---------------------------------------------
         */

        server.connectToMessageServer(
                host,
                port
        );


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


        System.out.println(
                "[" + serverName + "] exiting."
        );
    }
}
