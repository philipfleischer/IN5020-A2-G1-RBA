package src.bank_server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Converts account balances between currencies.
 *
 * The trading-rate file has one "<Currency> <rate>" line per currency,
 * where the rate is the value of 1 unit of that currency in USD
 * (e.g. "NOK 0.10" means 1 NOK = 0.10 USD).
 * USD is not in the file, it is always 1.0.
 */
public class CurrencyConverter {

    // currency -> value of 1 unit in USD
    private final Map<String, Double> rateToUsd = new HashMap<>();

    public CurrencyConverter() {
        rateToUsd.put("USD", 1.0);
    }

    // Reads the trading-rate file. Blank and malformed lines are skipped.
    public static CurrencyConverter fromFile(String path) throws IOException {

        CurrencyConverter converter = new CurrencyConverter();

        for (String line : Files.readAllLines(Paths.get(path))) {

            String[] parts = line.trim().split("\\s+");

            if (parts.length != 2) {
                continue;
            }

            try {
                converter.rateToUsd.put(parts[0], Double.parseDouble(parts[1]));
            } catch (NumberFormatException e) {
                // not a "<Currency> <number>" line, ignore it
            }
        }

        return converter;
    }

    // Total of all balances in the target currency. Throws if a rate is missing.
    public double convertTotal(Map<String, Double> balances, String target) {

        double totalInUsd = 0.0;

        for (Map.Entry<String, Double> entry : balances.entrySet()) {
            totalInUsd += entry.getValue() * rateOf(entry.getKey());
        }

        return totalInUsd / rateOf(target);
    }

    private double rateOf(String currency) {

        Double rate = rateToUsd.get(currency);

        if (rate == null || rate == 0.0) {
            throw new IllegalArgumentException("No trading rate for currency: " + currency);
        }

        return rate;
    }
}
