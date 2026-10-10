package src.message_delivery_server;
import java.rmi.Remote;
import java.rmi.RemoteException;

import src.bank_server.BankServerInterface;
import src.common.Transaction;

import java.util.List;

/*

Remote interface for the message delivery server. (what can be called remotely by the bank server)

*/
public interface MessageDeliveryServerInterface extends Remote {
    // we want to connect a bank server to the message delivery server
    public void join(String GroupName, BankServerInterface bankServer) throws RemoteException;

    // a bank server leaves the group on purpose (the "exit" command)
    public void leave(String groupName, BankServerInterface bankServer) throws RemoteException;

    // we want to broadcast a list of outstanding transactions to all registered bank servers
    public void broadcastTransactions(List<Transaction> transactions) throws RemoteException;

    // acknowledge to message delivery server that a transaction has been received
    public void ack(String serverId, long messageId) throws RemoteException;
}
