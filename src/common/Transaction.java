package src.common;

import java.io.Serializable;

/**
 * one deposit or addInterest (or a getSyncedBalance marker).
 * Serializable so it can be sent over RMI.
 */
public class Transaction implements Serializable {

    String command;
    String uniqueId;

    // When it was executed (shown in getHistory)
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

    // So the server log shows the command instead of src.common.Transaction@...
    @Override
    public String toString() {
        return command + " [" + uniqueId + "]";
    }
}
