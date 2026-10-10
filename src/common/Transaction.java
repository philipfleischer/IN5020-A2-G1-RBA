package src.common;

import java.io.Serializable;

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

    public Transaction parseTransaction(String transactionString) {
        String[] parts = transactionString.split(" ");
        this.command = parts[0];
        this.uniqueId = parts[1];
        return this;
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
}
