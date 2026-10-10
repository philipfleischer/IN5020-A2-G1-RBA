package src.bank_server;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

import src.common.Transaction;   

public interface BankServerInterface extends Remote {

    /**
     * Called by the MessageDeliveryServer when an ordered
     * transaction is delivered to this replica.
     */
    void receiveTransaction(long messageId, List<Transaction> transactions)
            throws RemoteException;


    /**
     * Called when the membership of the replica group changes,
     * for example when a BankServer joins or leaves.
     */
    void membershipChanged(List<String> members)
            throws RemoteException;


    /**
     * Used to give this BankServer the current replicated state,
     * for example when a new replica joins the group.
     */
    void updateState(BankState state)
            throws RemoteException;


    /**
     * Returns a copy of the current replicated state, used by the
     * MessageDeliveryServer to bring a late-joining replica up to date.
     */
    BankState getState()
            throws RemoteException;


    /**
     * Returns the unique name/id of this BankServer instance.
     */
    String getServerName()
            throws RemoteException;
}