package src.common;

import java.io.Serializable;

/**
 * One deposit or addInterest (or a getSyncedBalance marker), as defined in
 * the assignment:
 *
 *   command  = "<command name> <arguments>", e.g. "deposit NOK 500"
 *   uniqueId = "<bank server name> <outstanding_counter>"
 *
 * Serializable, because transactions are sent over RMI: from a bank server
 * to the MessageDeliveryServer, from there to all replicas, and inside
 * BankState to a replica that joins late.
 */
public class Transaction implements Serializable {

    String command;
    String uniqueId;

    // getHistory must print the timestamp of execution.
    // Travels with executedList when state is sent to a new replica.
    private String executedAt;

    public Transaction(String command, String uniqueId) {
        this.command = command;
        this.uniqueId = uniqueId;
    }

    public String getCommand() {
        return command;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public String getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(String executedAt) {
        this.executedAt = executedAt;
    }

    // Used when a list of transactions is printed, e.g. in the
    // MessageDeliveryServer log: "deposit NOK 500 [BankServer-1234-a3f2 0]"
    // instead of "src.common.Transaction@22fa7682".
    @Override
    public String toString() {
        return command + " [" + uniqueId + "]";
    }
}
