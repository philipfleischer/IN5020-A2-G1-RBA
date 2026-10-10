package src.common;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Puts a timestamp in front of every line the program prints.
 *
 * The assignment wants "the output log from ALL replicas - with executed
 * timestamp in each line". Instead of changing every println in the code,
 * we replace System.out and System.err once, at the start of main(), with
 * streams that add "[2026-10-10 14:03:27.512] " at the start of each line.
 *
 * This also covers lines we did not write ourselves, such as stack traces
 * and RMI error messages, and every new println added later.
 */
public final class Log {

    private Log() {
        // only static methods
    }

    /** Call this as the first line of every main(). */
    public static void install() {
        System.setOut(timestamped(System.out));
        System.setErr(timestamped(System.err));
    }

    private static PrintStream timestamped(PrintStream target) {
        // autoFlush = true: every println reaches the terminal/log file
        // at once, so lines from different processes keep their order
        return new PrintStream(
                new TimestampingOutputStream(target),
                true,
                StandardCharsets.UTF_8
        );
    }

    /*
     * Sits between PrintStream and the real System.out. It sees every byte
     * that is printed and writes the timestamp before the first byte of a
     * new line.
     *
     * The methods are synchronized: the command thread, the broadcast
     * thread and the RMI threads all print, and a timestamp must never end
     * up in the middle of another thread's line.
     */
    private static final class TimestampingOutputStream extends FilterOutputStream {

        private boolean atLineStart = true;

        TimestampingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public synchronized void write(int b) throws IOException {

            if (atLineStart) {
                out.write(
                        ("[" + Timestamp.now() + "] ")
                                .getBytes(StandardCharsets.UTF_8)
                );
                atLineStart = false;
            }

            out.write(b);

            if (b == '\n') {
                atLineStart = true;
            }
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            for (int i = off; i < off + len; i++) {
                write(b[i]);
            }
        }

        /*
         * An explicit flush without '\n' only happens after the interactive
         * prompt ("[BankServer-1234] > "). The user then types a command and
         * presses Enter, so the terminal is on a new line: the next output
         * must get a timestamp again.
         */
        @Override
        public synchronized void flush() throws IOException {
            atLineStart = true;
            out.flush();
        }
    }
}
