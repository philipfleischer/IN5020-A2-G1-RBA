package bank_server;

import common.Transaction;
import message_delivery_server.MessageDeliveryServerInterface;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

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

    private int orderCounter = 0;
    private int outstandingCounter = 0;

    private final String serverName;
    private final String accountName;
    private final int numberOfReplicas;

    // Remote reference to MessageDeliveryServer
    private MessageDeliveryServerInterface messageServer;

    // Handles command parsing
    private final BankServerHelper helper;


    /*
     * =====================================================
     * CONSTRUCTOR
     * =====================================================
     */

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

    public void connectToMessageServer(
            String host,
            int port
    ) throws Exception {

        Registry registry =
                LocateRegistry.getRegistry(host, port);

        messageServer =
                (MessageDeliveryServerInterface)
                        registry.lookup("MessageDeliveryServer");

        // Give MessageDeliveryServer a remote reference
        // to this BankServer
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

            for (Transaction transaction : transactions) {

                messageServer.broadcastTransaction(transaction);
            }

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
            Transaction transaction
    ) throws RemoteException {

        // Don't execute the same transaction twice
        if (hasBeenExecuted(transaction.getUniqueId())) {

            System.out.println(
                    "[" + serverName
                            + "] Duplicate ignored: "
                            + transaction.getUniqueId()
            );

            return;
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

        // If we originally created this transaction,
        // remove it from our outstanding collection
        outstandingCollection.removeIf(
                tx -> tx.getUniqueId()
                        .equals(transaction.getUniqueId())
        );
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

        double balance =
                balances.getOrDefault(currency, 0.0);

        System.out.println(
                "[" + serverName + "] "
                        + "Quick balance "
                        + currency + ": "
                        + balance
        );

        /*
         * TODO:
         *
         * Add currency conversion using the currency file.
         */
    }


    /*
     * =====================================================
     * SYNCED BALANCE
     * =====================================================
     */

    public void getSyncedBalance(
            String currency
    ) {

        /*
         * TODO:
         *
         * The assignment requires two implementations:
         *
         * 1. Naive implementation
         * 2. Correct ordered implementation
         *
         * We can implement this later.
         */

        System.out.println(
                "[" + serverName
                        + "] getSyncedBalance TODO"
        );
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
     */

    private boolean hasBeenExecuted(
            String uniqueId
    ) {

        for (Transaction transaction :
                executedList) {

            if (transaction.getUniqueId()
                    .equals(uniqueId)) {

                return true;
            }
        }

        return false;
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