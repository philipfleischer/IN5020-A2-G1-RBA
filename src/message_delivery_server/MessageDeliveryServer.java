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
                Message message =
                        messageQueue.take();


                System.out.println(
                        "Processing message: "
                                + currentMessageId
                );

                /*
                * Take a snapshot of the current replicas.
                */
                List<BankServerInterface> servers;

                synchronized (this) {
                        servers = new ArrayList<>(bankServers);
                }

                /*
                * For now we send sequentially.
                *
                * Later this must be changed to parallel
                * delivery + ACK waiting.
                */
                synchronized (ackLock) {
                        currentMessageId = message.getMessageId();

                        pendingAcks.clear();

                        for (BankServerInterface bankServer : servers) {
                                try {
                                        pendingAcks.add(
                                                bankServer.getServerName()
                                        );
                                } catch (RemoteException e) {
                                        System.err.println(
                                                "Could not get server name: "
                                                        + e.getMessage()
                                        );
                                }
                        }
                }

                // AFTER pendingAcks is ready:
                for (BankServerInterface bankServer : servers) {
                        try {
                                bankServer.receiveTransaction(
                                        message.getMessageId(),
                                        message.getTransactions()
                                );
                        } catch (RemoteException e) {
                                System.err.println(
                                        "Could not deliver message: "
                                                + e.getMessage()
                                );
                        }
                }
                
                // Wait for all ACKs to be received, then continue to the next message
                synchronized (ackLock) {
                        while (!pendingAcks.isEmpty()) {
                                ackLock.wait();
                        }
                }

                } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
                break;
                }
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