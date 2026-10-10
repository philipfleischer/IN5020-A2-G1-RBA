package src.bank_server;

public class BankServerHelper {

    // Used when getQuickBalance/getSyncedBalance is given without a
    // currency (as in Rep3.txt). USD is the base of the trading-rate file.
    private static final String DEFAULT_CURRENCY = "USD";

    private final BankServer server;

    public BankServerHelper(BankServer server) {
        this.server = server;
    }

    /**
     * Takes a command from the command line/input file
     * and decides what the BankServer should do.
     *
     * Returns false if the server should exit.
     */
    public boolean handleCommand(String input) {

        if (input == null || input.isBlank()) {
            return true;
        }

        String[] parts = input.trim().split("\\s+");
        String command = parts[0];

        try {

            switch (command) {

                case "deposit":
                    handleDeposit(parts);
                    break;

                case "addInterest":
                    handleAddInterest(parts);
                    break;

                case "getQuickBalance":
                    handleGetQuickBalance(parts);
                    break;

                case "getSyncedBalance":
                    handleGetSyncedBalance(parts);
                    break;

                case "getHistory":
                    handleGetHistory(parts);
                    break;

                case "checkTxStatus":
                    handleCheckTxStatus(parts);
                    break;

                case "cleanHistory":
                    handleCleanHistory(parts);
                    break;

                case "memberInfo":
                    handleMemberInfo(parts);
                    break;

                case "sleep":
                    handleSleep(parts);
                    break;

                case "exit":
                    return false;

                default:
                    System.out.println(
                            "Unknown command: " + command);
            }

        } catch (NumberFormatException e) {
            System.out.println(
                    "Invalid number in command: " + input);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Sleep interrupted.");
        }

        return true;
    }

    /*
     * -------------------------------------------------
     * deposit <Currency> <amount>
     * -------------------------------------------------
     */

    private void handleDeposit(String[] parts) {

        if (parts.length != 3) {
            System.out.println(
                    "Usage: deposit <Currency> <amount>");
            return;
        }

        String currency = parts[1];

        // Parse only to VALIDATE the amount: "abc" throws
        // NumberFormatException, which handleCommand catches and reports.
        Double.parseDouble(parts[2]);

        /*
         * IMPORTANT:
         *
         * We do NOT change the balance here.
         *
         * BankServer creates an outstanding transaction.
         * The balance should only change when the ordered
         * transaction comes back from MessageDeliveryServer.
         *
         * The command is stored exactly as the user typed it
         * ("deposit NOK 500"), as in the assignment text.
         */
        server.createTransaction(
                "deposit " + currency + " " + parts[2]);
    }

    /*
     * -------------------------------------------------
     * addInterest <Currency> <percent>
     *
     * OR
     *
     * addInterest <percent>
     * -------------------------------------------------
     */

    private void handleAddInterest(String[] parts) {

        if (parts.length == 3) {

            String currency = parts[1];
            Double.parseDouble(parts[2]); // validate only

            server.createTransaction(
                    "addInterest " + currency + " " + parts[2]);

        } else if (parts.length == 2) {

            Double.parseDouble(parts[1]); // validate only

            server.createTransaction(
                    "addInterest " + parts[1]);

        } else {

            System.out.println(
                    "Usage: addInterest [Currency] <percent>");
        }
    }

    /*
     * -------------------------------------------------
     * getQuickBalance [Currency] (default: USD)
     * -------------------------------------------------
     */

    private void handleGetQuickBalance(String[] parts) {

        if (parts.length > 2) {
            System.out.println(
                    "Usage: getQuickBalance [Currency]");
            return;
        }

        String currency = parts.length == 2 ? parts[1] : DEFAULT_CURRENCY;

        server.getQuickBalance(currency);
    }

    /*
     * -------------------------------------------------
     * getSyncedBalance [Currency] (default: USD)
     * -------------------------------------------------
     */

    private void handleGetSyncedBalance(String[] parts) {

        if (parts.length > 2) {
            System.out.println(
                    "Usage: getSyncedBalance [Currency]");
            return;
        }

        String currency = parts.length == 2 ? parts[1] : DEFAULT_CURRENCY;

        // The implementation is chosen at start-up with -Dsync=naive|correct
        if (BankServer.NAIVE_SYNC) {
            server.startSyncedBalanceNaive(currency);
        } else {
            server.getSyncedBalance(currency);
        }
    }

    /*
     * -------------------------------------------------
     * getHistory
     * -------------------------------------------------
     */

    private void handleGetHistory(String[] parts) {

        if (parts.length != 1) {
            System.out.println(
                    "Usage: getHistory");
            return;
        }

        server.getHistory();
    }

    /*
     * -------------------------------------------------
     * checkTxStatus <unique_id>
     * -------------------------------------------------
     */

    private void handleCheckTxStatus(String[] parts) {

        /*
         * NOTE:
         *
         * The assignment describes unique_id as something
         * containing the BankServer instance name and the
         * outstanding counter.
         *
         * Depending on how you format your IDs, you may
         * need to change this parsing.
         */

        if (parts.length < 2) {
            System.out.println(
                    "Usage: checkTxStatus <unique_id>");
            return;
        }

        /*
         * Join everything after checkTxStatus back together.
         *
         * This allows IDs such as:
         *
         * BankServerA 3
         */
        StringBuilder idBuilder = new StringBuilder();

        for (int i = 1; i < parts.length; i++) {

            if (i > 1) {
                idBuilder.append(" ");
            }

            idBuilder.append(parts[i]);
        }

        String uniqueId = idBuilder.toString();

        server.checkTxStatus(uniqueId);
    }

    /*
     * -------------------------------------------------
     * cleanHistory
     * -------------------------------------------------
     */

    private void handleCleanHistory(String[] parts) {

        if (parts.length != 1) {
            System.out.println(
                    "Usage: cleanHistory");
            return;
        }

        server.cleanHistory();
    }

    /*
     * -------------------------------------------------
     * memberInfo
     * -------------------------------------------------
     */

    private void handleMemberInfo(String[] parts) {

        if (parts.length != 1) {
            System.out.println(
                    "Usage: memberInfo");
            return;
        }

        server.memberInfo();
    }

    /*
     * -------------------------------------------------
     * sleep <duration>
     * -------------------------------------------------
     */

    private void handleSleep(String[] parts)
            throws InterruptedException {

        if (parts.length != 2) {
            System.out.println(
                    "Usage: sleep <duration>");
            return;
        }

        long duration = Long.parseLong(parts[1]);

        if (duration < 0) {
            System.out.println(
                    "Sleep duration cannot be negative.");
            return;
        }

        /*
         * Assuming duration is given in seconds.
         * Change this if your assignment specifies
         * another unit.
         */
        Thread.sleep(duration * 1000);
    }
}
