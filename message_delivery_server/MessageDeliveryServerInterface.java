package message_delivery_server;
import java.rmi.Remote;
import java.rmi.RemoteException;
import common.Transaction;

import bank_server.BankServerInterface;

/*

Remote interface for the message delivery server. (what can be called remotely by the bank server)

*/
public interface MessageDeliveryServerInterface extends Remote {
    // we want to connect a bank server to the message delivery server
    public void join(String GroupName, BankServerInterface bankServer) throws RemoteException;

    // we want to broadcast transaction to all registered bank servers
    public void broadcastTransaction(Transaction transaction) throws RemoteException;

    // acknowledge to message delivery server that a transaction has been received
    public void ack(String serverId, String transactionId) throws RemoteException;
}
