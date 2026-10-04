package message_delivery_server;

import bank_server.BankServerInterface;
import common.Transaction;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.util.ArrayList;
import java.util.List;

public class MessageDeliveryServer
        extends UnicastRemoteObject
        implements MessageDeliveryServerInterface {

    // BankServer replicas that have joined
    private final List<BankServerInterface> bankServers =
            new ArrayList<>();


    /*
     * Constructor required because UnicastRemoteObject
     * can throw RemoteException.
     */
    public MessageDeliveryServer()
            throws RemoteException {

        super();
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
     * A BankServer sends a transaction here.
     *
     * MessageDeliveryServer then sends it to every
     * BankServer replica.
     */
    @Override
    public void broadcastTransaction(
            Transaction transaction
    ) throws RemoteException {

        System.out.println(
                "Broadcasting transaction: "
                        + transaction.getUniqueId()
        );

        /*
         * Make a copy of the list.
         *
         * This prevents problems if a BankServer joins
         * while we are iterating.
         */
        List<BankServerInterface> servers;

        synchronized (this) {
            servers = new ArrayList<>(bankServers);
        }


        for (BankServerInterface bankServer : servers) {

            try {

                bankServer.receiveTransaction(
                        transaction
                );

            } catch (RemoteException e) {

                System.err.println(
                        "Could not deliver transaction to a BankServer: "
                                + e.getMessage()
                );

                /*
                 * TODO:
                 *
                 * Later:
                 * - retry after 2 seconds
                 * - after 5 seconds consider server failed
                 * - remove failed server
                 */
            }
        }
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
            String transactionId
    ) throws RemoteException {

        System.out.println(
                "ACK from "
                        + serverId
                        + " for transaction "
                        + transactionId
        );

        // TODO:
        // Store/track acknowledgements
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