package at.marcelr.mrwaitroom.link;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * What happened on this server, for MRadmin's page Log (source "waiting room"): started, stopping, restarts, the main
 * server going down and coming back, configuration and updates from MRadmin. Kept in memory (the newest 500) - MRadmin
 * asks for them every few seconds through the agent and stores them in its own log, so nothing is lost while this
 * server runs. Texts in English like the console (they are the same lines).
 */
public final class Events {

    /** One event: time (epoch ms), short name, text, colour (0xRRGGBB, the same palette as MRadmin's log). */
    public record Event(long time, String event, String text, int color) {
    }

    public static final int GOOD = 0x22C55E;
    public static final int INFO = 0x3B82F6;
    public static final int WARN = 0xF59E0B;
    public static final int BAD = 0xE5484D;
    private static final int KEEP = 500;

    private final Deque<Event> events = new ArrayDeque<>();
    private long last;

    /** Adds an event (any thread). Two in the same millisecond get different times, so "after" never skips one. */
    public synchronized void add(String event, String text, int color) {
        long time = Math.max(System.currentTimeMillis(), last + 1);
        last = time;
        events.addLast(new Event(time, event, text, color));
        while (events.size() > KEEP) {
            events.removeFirst();
        }
    }

    /** The events after {@code after} (epoch ms), oldest first. */
    public synchronized List<Event> after(long after) {
        return events.stream().filter(e -> e.time() > after).toList();
    }
}
