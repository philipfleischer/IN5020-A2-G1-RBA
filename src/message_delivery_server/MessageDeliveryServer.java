package src.message_delivery_server;

import src.bank_server.BankServerInterface;
import src.common.Transaction;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashSet;
import src.common.Message;

public class MessageDeliveryServer
        extends UnicastRemoteObject
        implements MessageDeliveryServerInterface {

    // BankServer replicas that have joined
    private final List<BankServerInterface> bankServers =
            new ArrayList<>();

    // Queue of messages to be broadcast to all replicas
    private final BlockingQueue<Message> messageQueue =
        new LinkedBlockingQueue<>();

    // IDs of BankServers we are still waiting for ACKs from
    private final Set<String> pendingAcks = new HashSet<>();

    private final Object ackLock = new Object();

    // Timeouts for ACK handling
    private static final long RESEND_AFTER_MS = 2000;
    private static final long FAIL_AFTER_MS = 5000;

    // Threads used to send a message to all replicas in parallel
    private final ExecutorService sender =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });

    private long currentMessageId = -1;
    private long nextMessageId = 0;


    /*
     * Constructor required because UnicastRemoteObject
     * can throw RemoteException.
     */
    public MessageDeliveryServer()
            throws RemoteException {

        super();

        Thread worker = new Thread(
            this::processMessages
        );

        worker.start();
    }


    /*
     * -------------------------------------------------
     * JOIN
     * -------------------------------------------------
     *
     * Called when a BankServer starts and joins the group.
     */
    @Override
    public synchronized void join(
            String groupName,
            BankServerInterface bankServer
    ) throws RemoteException {

        bankServers.add(bankServer);

        String serverName =
                bankServer.getServerName();

        System.out.println(
                serverName
                        + " joined group "
                        + groupName
        );

        System.out.println(
                "Number of replicas: "
                        + bankServers.size()
        );

        // Tell all BankServers that membership changed
        notifyMembershipChanged();
    }


    /*
     * -------------------------------------------------
     * BROADCAST TRANSACTION
     * -------------------------------------------------
     *
     * A BankServer sends a list of transactions here.
     *
     * MessageDeliveryServer adds the list to the message queue, and will later broadcast it to all
     * BankServer replicas.
     * 
     * (bank servers can call at the same time) synchronized to have safe nextMessageId++.
     */
    @Override
    public synchronized void broadcastTransactions(
        List<Transaction> transactions
    ) throws RemoteException {

        // Make our own copy of the received message
        List<Transaction> message =
                new ArrayList<>(transactions);

        Message msg = new Message(nextMessageId++, message);
        messageQueue.add(msg);

        System.out.println(
                "Message added to queue: " + message
        );
    }


    /*
     * -------------------------------------------------
     * MEMBERSHIP CHANGED
     * -------------------------------------------------
     */

    private void notifyMembershipChanged() {

        List<String> memberNames =
                new ArrayList<>();


        /*
         * First collect the names of all members.
         */
        for (BankServerInterface bankServer : bankServers) {

            try {

                memberNames.add(
                        bankServer.getServerName()
                );

            } catch (RemoteException e) {

                System.err.println(
                        "Could not get BankServer name: "
                                + e.getMessage()
                );
            }
        }


        /*
         * Then send the updated membership list
         * to every BankServer.
         */
        for (BankServerInterface bankServer : bankServers) {

            try {

                bankServer.membershipChanged(
                        memberNames
                );

            } catch (RemoteException e) {

                System.err.println(
                        "Could not send membership update: "
                                + e.getMessage()
                );
            }
        }
    }


    /*
     * -------------------------------------------------
     * ACK
     * -------------------------------------------------
     *
     * We will implement this properly when we add
     * retry/failure handling.
     */
    @Override
    public void ack(
            String serverId,
            long messageId
    ) throws RemoteException {

        synchronized (ackLock) {
            // Ignore ACKs for messages other than the current message
            if (messageId != currentMessageId) {
                return;
            }

            pendingAcks.remove(serverId);

            System.out.println(
                    "ACK from "
                            + serverId
                            + " for message "
                            + messageId
            );

            // if all ACKs have been received, notify the waiting thread
            if (pendingAcks.isEmpty()) {
                ackLock.notifyAll();
            }
        }
    }

    private void processMessages() {

        while (true) {

            try {

                // Wait here until a message exists
                Message message = messageQueue.take();

                System.out.println(
                        "Processing message: " + message.getMessageId()
                );

                // Snapshot of the current replicas, keyed by name
                List<BankServerInterface> servers;

                synchronized (this) {
                    servers = new ArrayList<>(bankServers);
                }

                Map<String, BankServerInterface> byName =
                        new LinkedHashMap<>();

                for (BankServerInterface bankServer : servers) {
                    try {
                        byName.put(bankServer.getServerName(), bankServer);
                    } catch (RemoteException e) {
                        // Cannot even ask for its name: the replica is dead
                        markFailed("unreachable replica", bankServer);
                    }
                }

                // Prepare pendingAcks BEFORE sending
                synchronized (ackLock) {
                    currentMessageId = message.getMessageId();
                    pendingAcks.clear();
                    pendingAcks.addAll(byName.keySet());
                }

                // Send to all replicas in parallel
                for (BankServerInterface bankServer : byName.values()) {
                    sendAsync(bankServer, message);
                }

                // Wait for ACKs: resend after 2 s, give up after 5 s
                long start = System.currentTimeMillis();
                boolean resent = false;
                List<String> failed;

                synchronized (ackLock) {

                    while (!pendingAcks.isEmpty()) {

                        long elapsed = System.currentTimeMillis() - start;

                        if (elapsed >= FAIL_AFTER_MS) {
                            break;
                        }

                        if (!resent && elapsed >= RESEND_AFTER_MS) {
                            resent = true;
                            for (String name : pendingAcks) {
                                System.out.println(
                                        "No ACK from " + name
                                                + " for message "
                                                + message.getMessageId()
                                                + ", resending"
                                );
                                sendAsync(byName.get(name), message);
                            }
                            continue;
                        }

                        long nextDeadline =
                                resent ? FAIL_AFTER_MS : RESEND_AFTER_MS;

                        ackLock.wait(nextDeadline - elapsed);
                    }

                    failed = new ArrayList<>(pendingAcks);
                }

                // Replicas that never answered are marked FAIL and removed
                for (String name : failed) {
                    markFailed(name, byName.get(name));
                }

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
                break;
            }
        }
    }


    /*
     * Deliver a message to one replica on its own thread,
     * so a slow or dead replica cannot block the others.
     */
    private void sendAsync(
            BankServerInterface bankServer,
            Message message
    ) {
        sender.submit(() -> {
            try {
                bankServer.receiveTransaction(
                        message.getMessageId(),
                        message.getTransactions()
                );
            } catch (RemoteException e) {
                System.err.println(
                        "Could not deliver message: " + e.getMessage()
                );
            }
        });
    }


    /*
     * A replica that did not ACK within 5 s is marked FAIL,
     * removed from the group, and the others are notified.
     */
    private void markFailed(String name, BankServerInterface bankServer) {

        System.out.println(
                "FAIL: " + name
                        + " did not ACK in time, removing it from the group"
        );

        synchronized (this) {
            bankServers.remove(bankServer);
            notifyMembershipChanged();
        }
    }


    /*
     * -------------------------------------------------
     * MAIN
     * -------------------------------------------------
     */

    public static void main(
            String[] args
    ) throws Exception {

        int port = 1099;

        /*
         * Optional:
         *
         * java MessageDeliveryServer 1099
         */
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }


        MessageDeliveryServer server =
                new MessageDeliveryServer();


        /*
         * Create the RMI registry.
         */
        Registry registry =
                LocateRegistry.createRegistry(port);


        /*
         * Register MessageDeliveryServer so BankServers
         * can find it using lookup().
         */
        registry.rebind(
                "MessageDeliveryServer",
                server
        );


        System.out.println(
                "MessageDeliveryServer ready on port "
                        + port
        );
    }
}