package src.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One place that decides how timestamps look, so getHistory and
 * every log line use exactly the same format.
 */
public final class Timestamp {

    // DateTimeFormatter is immutable and thread-safe,
    // so one shared instance is fine even though the
    // RMI thread, the broadcast thread and the command thread all use it.
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private Timestamp() {
        // only static methods
    }

    /** Current local time, e.g. "2026-10-10 14:03:27.512". */
    public static String now() {
        return LocalDateTime.now().format(FORMAT);
    }
}
