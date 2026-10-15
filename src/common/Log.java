package src.common;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Adds a timestamp at the start of every line we print.
 * We swap out System.out and System.err once in main(),
 * so we do not have to change every println.
 */
public final class Log {

    private Log() {
        // only static methods
    }

    // The stream behind System.out, kept so promptAnswered() can reach it
    private static TimestampingOutputStream stdout;

    /** Call this as the first line of every main(). */
    public static void install() {
        stdout = new TimestampingOutputStream(System.out);
        System.setOut(timestamped(stdout));
        System.setErr(timestamped(new TimestampingOutputStream(System.err)));
    }

    /**
     * Call after the user presses Enter. The prompt has no newline,
     * so we tell the stream the next line needs a timestamp.
     */
    public static void promptAnswered() {
        if (stdout != null) {
            stdout.startNewLine();
        }
    }

    private static PrintStream timestamped(TimestampingOutputStream stream) {
        // autoFlush so every line is written right away
        return new PrintStream(stream, true, StandardCharsets.UTF_8);
    }

    /*
     * Writes a timestamp before the first character of each new line.
     * synchronized, since several threads print at the same time.
     */
    private static final class TimestampingOutputStream extends FilterOutputStream {

        private boolean atLineStart = true;

        TimestampingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public synchronized void write(int b) throws IOException {

            if (atLineStart) {
                out.write(("[" + Timestamp.now() + "] ").getBytes(StandardCharsets.UTF_8));
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

        //Only "\n" or promptAnswered() starts a new line.
        // We cannot use flush for this, Java 11 flushes in the middle of lines
        synchronized void startNewLine() {
            atLineStart = true;
        }
    }
}
