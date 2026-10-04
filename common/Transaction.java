package common;

public class Transaction {
    
    String command;
    String uniqueId;

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
}
