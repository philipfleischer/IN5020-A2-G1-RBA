package src.message_delivery_server;

import src.bank_server.BankServerInterface;
import src.common.Log;
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
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import src.bank_server.BankState;
import java.util.HashSet;
import src.common.Message;

public class MessageDeliveryServer
        extends UnicastRemoteObject
        implements MessageDeliveryServerInterface {

    // BankServer replicas that have joined
    private final List<BankServerInterface> bankServers = new ArrayList<>();

    // Name of every member, saved at join. We never ask a member for its
    // name again: a replica that hangs would never answer, and the call
    // would block the server forever (RMI calls have no timeout).
    private final Map<BankServerInterface, String> names = new HashMap<>();

    // Queue of messages to be broadcast to all replicas
    private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();

    // IDs of BankServers we are still waiting for ACKs from
    private final Set<String> pendingAcks = new HashSet<>();

    private final Object ackLock = new Object();

    // Held while one message is delivered, and while a replica joins
    private final ReentrantLock deliveryLock = new ReentrantLock();

    // Timeouts for ACK handling
    private static final long RESEND_AFTER_MS = 2000;
    private static final long FAIL_AFTER_MS = 5000;

    // Timeouts for other calls to a replica (state transfer, member list)
    private static final long STATE_TRANSFER_TIMEOUT_MS = 2000;
    private static final long NOTIFY_TIMEOUT_MS = 1000;

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
                this::processMessages);

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
    public void join(
            String groupName,
            BankServerInterface bankServer
    ) throws RemoteException {

        // Wait until the message currently being delivered is finished,
        // so every existing replica has the same, up-to-date state.
        deliveryLock.lock();

        try {

            List<BankServerInterface> existing;

            synchronized (this) {
                existing = new ArrayList<>(bankServers);
            }

            // Late join: copy the state from the first replica that answers
            for (BankServerInterface other : existing) {

                try {

                    // With a timeout: a replica that hangs must not
                    // block the join (we then try the next one)
                    BankState state = callWithTimeout(
                            other::getState, STATE_TRANSFER_TIMEOUT_MS);
                    bankServer.updateState(state);
                    break;

                } catch (Exception e) {

                    System.err.println(
                            "State transfer failed, trying next replica: "
                                    + e.getMessage()
                    );
                }
            }

            synchronized (this) {

                // The joining replica is calling us, so it is alive
                // and answers this one at once
                String serverName = bankServer.getServerName();

                bankServers.add(bankServer);
                names.put(bankServer, serverName);

                System.out.println(
                        serverName + " joined group " + groupName
                );

                System.out.println(
                        "Number of replicas: " + bankServers.size()
                );

                // Tell all BankServers that membership changed
                notifyMembershipChanged();
            }

        } finally {
            deliveryLock.unlock();
        }
    }


    /*
     * -------------------------------------------------
     * LEAVE
     * -------------------------------------------------
     *
     * Called by a BankServer that exits on purpose. This is a
     * "leave", not a crash: we remove it at once instead of
     * waiting for the ACK timeout to mark it as FAIL.
     */
    @Override
    public void leave(
            String groupName,
            BankServerInterface bankServer) throws RemoteException {

        String serverName;

        synchronized (this) {

            serverName = names.containsKey(bankServer)
                    ? names.get(bankServer)
                    : bankServer.getServerName();

            // RMI stubs that point to the same remote object are
            // equal(), so this removes the stub that join() stored.
            bankServers.remove(bankServer);
            names.remove(bankServer);

            System.out.println(
                    serverName
                            + " left group "
                            + groupName);

            System.out.println(
                    "Number of replicas: "
                            + bankServers.size());

            notifyMembershipChanged();
        }

        // If the worker thread is waiting for an ACK from this
        // server, stop waiting for it: it will never come.
        synchronized (ackLock) {

            pendingAcks.remove(serverName);

            if (pendingAcks.isEmpty()) {
                ackLock.notifyAll();
            }
        }
    }

    /*
     * -------------------------------------------------
     * BROADCAST TRANSACTION
     * -------------------------------------------------
     *
     * A BankServer sends a list of transactions here.
     *
     * MessageDeliveryServer adds the list to the message queue, and will later
     * broadcast it to all
     * BankServer replicas.
     *
     * (bank servers can call at the same time) synchronized to have safe
     * nextMessageId++.
     */
    @Override
    public synchronized void broadcastTransactions(
            List<Transaction> transactions) throws RemoteException {

        // Make our own copy of the received message
        List<Transaction> message = new ArrayList<>(transactions);

        Message msg = new Message(nextMessageId++, message);
        messageQueue.add(msg);

        System.out.println(
                "Message added to queue: " + message);
    }

    /*
     * -------------------------------------------------
     * MEMBERSHIP CHANGED
     * -------------------------------------------------
     */

    private void notifyMembershipChanged() {

        List<String> memberNames = new ArrayList<>();

        /*
         * First collect the names of all members (saved at join,
         * so no remote calls are needed).
         */
        for (BankServerInterface bankServer : bankServers) {
            memberNames.add(names.get(bankServer));
        }

        /*
         * Then send the updated membership list to every BankServer.
         * One at a time, so every replica sees the lists in the same
         * order, but with a timeout, so a replica that hangs cannot
         * block the server (it is removed by the ACK timeout later).
         */
        for (BankServerInterface bankServer : bankServers) {

            try {

                callWithTimeout(() -> {
                    bankServer.membershipChanged(memberNames);
                    return null;
                }, NOTIFY_TIMEOUT_MS);

            } catch (Exception e) {

                System.err.println(
                        "Could not send membership update to "
                                + names.get(bankServer) + ": " + e);
            }
        }
    }

    /*
     * -------------------------------------------------
     * ACK
     * -------------------------------------------------
     *
     * Called by a BankServer when it has received AND executed
     * a message. deliver() waits for these (resend after 2 s,
     * FAIL after 5 s).
     */
    @Override
    public void ack(
            String serverId,
            long messageId) throws RemoteException {

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
                            + messageId);

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

                // No replica can join while this message is being
                // delivered (see join()), so state transfers stay consistent
                deliveryLock.lock();
                try {
                    deliver(message);
                } finally {
                    deliveryLock.unlock();
                }

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
                break;
            }
        }
    }


    /*
     * Deliver one message to all replicas and wait for ACKs.
     */
    private void deliver(Message message) throws InterruptedException {

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

        synchronized (this) {
            for (BankServerInterface bankServer : servers) {
                byName.put(names.get(bankServer), bankServer);
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
            names.remove(bankServer);
            notifyMembershipChanged();
        }
    }


    /*
     * Runs a remote call on another thread and gives up after timeoutMs.
     * Used for calls the server makes while holding a lock, so one
     * frozen replica cannot stop the whole group.
     */
    private <T> T callWithTimeout(Callable<T> call, long timeoutMs)
            throws Exception {

        Future<T> future = sender.submit(call);

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof Exception ? (Exception) cause : e;
        }
    }


    /*
     * -------------------------------------------------
     * MAIN
     * -------------------------------------------------
     */

    public static void main(
            String[] args) throws Exception {

        // Timestamp on every output line
        Log.install();

        int port = 1099;

        /*
         * Optional:
         *
         * java MessageDeliveryServer 1099
         */
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }

        MessageDeliveryServer server = new MessageDeliveryServer();

        /*
         * Create the RMI registry.
         */
        Registry registry = LocateRegistry.createRegistry(port);

        /*
         * Register MessageDeliveryServer so BankServers
         * can find it using lookup().
         */
        registry.rebind(
                "MessageDeliveryServer",
                server);

        System.out.println(
                "MessageDeliveryServer ready on port "
                        + port);
    }
} 