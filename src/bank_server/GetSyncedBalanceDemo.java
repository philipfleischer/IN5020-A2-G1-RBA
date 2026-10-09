package src.bank_server;

/**
 * Demo: naive vs correct getSyncedBalance under continuous load.
 *
 * Run (with a MessageDeliveryServer already running):
 *   java -cp out src.bank_server.GetSyncedBalanceDemo localhost:1099
 *
 * What it shows:
 *  - A background thread keeps outstanding_collection non-empty: right after
 *    every 10 s broadcast it creates a new deposit, before the ordered
 *    delivery of the broadcast comes back.
 *  - The NAIVE getSyncedBalance waits for an empty outstanding_collection, so
 *    it stays blocked for as long as new transactions keep arriving.
 *  - The CORRECT getSyncedBalance is ordered like any other transaction, so
 *    it returns after the next broadcast no matter how much load there is.
 *  - When the load stops, outstanding_collection finally drains and the
 *    naive call returns too.
 */
public class GetSyncedBalanceDemo {

    private static final long BROADCAST_INTERVAL_MS = 10_000;
    private static final long LOAD_PHASE_MS = 35_000;

    private static volatile boolean keepCreatingLoad = true;

    public static void main(String[] args) throws Exception {

        String[] address =
                (args.length > 0 ? args[0] : "localhost:1099").split(":");

        BankServer server = new BankServer("DemoBank", "demo", 1);
        server.connectToMessageServer(address[0], Integer.parseInt(address[1]));

        long start = System.currentTimeMillis();

        // Something outstanding from the very start.
        server.createTransaction("deposit USD 1");

        // Stand-in for the real 10 s broadcast thread, so the demo controls
        // the exact order: broadcast first, then immediately a new deposit.
        Thread broadcaster = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(BROADCAST_INTERVAL_MS);
                } catch (InterruptedException e) {
                    return;
                }
                server.broadcastOutstanding();
                if (keepCreatingLoad) {
                    server.createTransaction("deposit USD 1");
                }
            }
        });
        broadcaster.setDaemon(true);
        broadcaster.start();

        Thread.sleep(1_000);

        Thread naive = new Thread(() -> {
            server.getSyncedBalanceNaive("USD");
            System.out.println("### NAIVE returned after "
                    + seconds(start) + " s");
        });
        Thread correct = new Thread(() -> {
            server.getSyncedBalance("USD");
            System.out.println("### CORRECT returned after "
                    + seconds(start) + " s");
        });

        System.out.println("### t=" + seconds(start)
                + " s: calling naive and correct getSyncedBalance under load");
        naive.setDaemon(true);
        correct.setDaemon(true);
        naive.start();
        correct.start();

        Thread.sleep(LOAD_PHASE_MS);

        System.out.println("### t=" + seconds(start) + " s: naive is "
                + (naive.isAlive() ? "STILL BLOCKED" : "done")
                + ", correct is "
                + (correct.isAlive() ? "still blocked" : "done"));

        System.out.println("### Stopping the load...");
        keepCreatingLoad = false;

        naive.join(30_000);
        System.out.println("### t=" + seconds(start) + " s: naive is "
                + (naive.isAlive() ? "still blocked" : "done")
                + " (it needed the load to stop)");

        System.exit(0);
    }

    private static String seconds(long start) {
        return String.format("%.1f", (System.currentTimeMillis() - start) / 1000.0);
    }
}