package src.bank_server;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import src.common.Transaction;

public class BankState implements Serializable {

    private Map<String, Double> balances;
    private List<Transaction> executedList;
    private int orderCounter;

    // constructor
    public BankState(Map<String, Double> balances, List<Transaction> executedList, int orderCounter) {
        this.balances = balances;
        this.executedList = executedList;
        this.orderCounter = orderCounter;
    }

    // getters
    public Map<String, Double> getBalances() {
        return balances;
    }

    public List<Transaction> getExecutedList() {
        return executedList;
    }

    public int getOrderCounter() {
        return orderCounter;
    }
}
