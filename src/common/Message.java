package src.common;
import java.util.List;

public class Message {
    private final long messageId;
    private final List<Transaction> transactions;

    // constructor + getters
    public Message(long messageId, List<Transaction> transactions) {
        this.messageId = messageId;
        this.transactions = transactions;
    }

    public long getMessageId() {
        return messageId;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }
}
