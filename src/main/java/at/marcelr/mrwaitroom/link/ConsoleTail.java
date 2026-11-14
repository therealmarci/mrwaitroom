package at.marcelr.mrwaitroom.link;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The console of this server for MRadmin's web console (agent): follows logs/latest.log in a background thread and
 * keeps the newest 1000 lines with a running number - like MRadmin's ConsoleLog. Colour codes are removed here, links
 * and IP addresses are hidden by MRadmin (its settings web.console.hide-*). Starts with the end of the current file.
 */
final class ConsoleTail {

    /** One line: running number, level (info, warn, error - a line without one keeps the one before) and text. */
    record Line(long seq, String level, String text) {
    }

    private static final long POLL_MILLIS = 500;
    private static final long START_BYTES = 256 * 1024;
    private static final int KEEP = 1000;

    private final Path file;
    private final Deque<Line> lines = new ArrayDeque<>();
    private long seq;
    private long position = -1;
    private String level = "info";
    private final StringBuilder partial = new StringBuilder();
    private volatile Thread thread;

    ConsoleTail(Path serverFolder) {
        this.file = serverFolder.resolve("logs").resolve("latest.log");
    }

    void start() {
        stop();
        Thread t = Thread.ofVirtual().name("MRwaitroom-ConsoleTail").unstarted(this::run);
        thread = t;
        t.start();
    }

    void stop() {
        Thread t = thread;
        thread = null;
        if (t != null) {
            t.interrupt();
        }
    }

    /** Lines after {@code after} (at most {@code max}, the newest), oldest first. */
    synchronized List<Line> after(long after, int max) {
        List<Line> result = new ArrayList<>();
        for (Line line : lines) {
            if (line.seq() > after) {
                result.add(line);
            }
        }
        return result.size() > max ? result.subList(result.size() - max, result.size()) : result;
    }

    synchronized long last() {
        return seq;
    }

    private void run() {
        while (thread == Thread.currentThread()) {
            try {
                read();
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                return;
            } catch (IOException | RuntimeException e) {
                position = -1;
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    return;
                }
            }
        }
    }

    private void read() throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        long size = Files.size(file);
        if (position < 0) {
            position = Math.max(0, size - START_BYTES);
            partial.setLength(0);
            if (position > 0) {
                partial.append('\u0000'); // the first (cut) line is dropped
            }
        } else if (size < position) {
            position = 0;
            partial.setLength(0);
        }
        if (size == position) {
            return;
        }
        byte[] data;
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(position);
            data = new byte[(int) Math.min(size - position, 4 * 1024 * 1024)];
            raf.readFully(data);
        }
        position += data.length;
        partial.append(new String(data, StandardCharsets.UTF_8));
        int newline;
        List<String> complete = new ArrayList<>();
        while ((newline = partial.indexOf("\n")) >= 0) {
            String line = partial.substring(0, newline);
            partial.delete(0, newline + 1);
            if (!line.startsWith("\u0000")) {
                complete.add(line);
            }
        }
        if (!complete.isEmpty()) {
            add(complete);
        }
    }

    private synchronized void add(List<String> raw) {
        for (String line : raw) {
            String text = LogFiles.clean(line, false, false);
            String own = LogFiles.level(text);
            level = own == null ? level : own;
            lines.addLast(new Line(++seq, level, text));
        }
        while (lines.size() > KEEP) {
            lines.removeFirst();
        }
    }
}
