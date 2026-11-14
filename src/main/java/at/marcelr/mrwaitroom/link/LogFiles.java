package at.marcelr.mrwaitroom.link;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Copy of MRadmin's ServerLogFiles (keep both the same) - MRadmin asks for it through the agent. Reads Paper's files in {@code logs/} ({@code latest.log} = today,
 * {@code <date>-<n>.log.gz} = earlier, oldest number first) and finds lines by time, text and level, newest first.
 * Nothing is kept - Paper keeps the files itself. Links and IP addresses are hidden like in the web console.
 * <p>
 * Every line gets a key that orders it: its time (date from the file name, time from {@code [HH:mm:ss]} at the start;
 * lines without one, e.g. stack traces, take the one before) plus a running number within that second. The page
 * asks for "older than key" to load more. The level (info, warn, error) comes from {@code [thread/LEVEL]} or
 * {@code [HH:mm:ss LEVEL]}; lines without one keep the level of the line before.
 */
public final class LogFiles {

    /** One line: {@code key} for ordering and "load older", {@code time} in epoch ms, level info/warn/error. */
    public record Line(long key, long time, String level, String text) {
    }

    /** @param counts lines per level in the time range that match the text (null if not asked for) */
    record Result(List<Line> lines, boolean more, Map<String, Integer> counts) {
    }

    public static final int MAX_DAYS = 14;
    public static final List<String> LEVELS = List.of("info", "warn", "error");
    /** Parsed finished days, see read(). */
    private static final java.util.Map<String, java.lang.ref.SoftReference<List<Line>>> DAYS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Pattern GZ = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})-(\\d+)\\.log\\.gz");
    private static final Pattern TIME = Pattern.compile("^\\[(\\d{2}):(\\d{2}):(\\d{2})");
    private static final Pattern LEVEL = Pattern.compile("^(?:\\[\\d{2}:\\d{2}:\\d{2}\\] )?\\[[^\\]]*?[/ ](INFO|WARN|ERROR|FATAL|DEBUG|TRACE)\\]");

    private final Path folder;
    private final ZoneId zone;

    public LogFiles(Path folder, ZoneId zone) {
        this.folder = folder;
        this.zone = zone;
    }

    /** info, warn or error of a line with a level at its start, otherwise null. */
    public static String level(String line) {
        Matcher m = LEVEL.matcher(line);
        if (!m.find()) {
            return null;
        }
        return switch (m.group(1)) {
            case "WARN" -> "warn";
            case "ERROR", "FATAL" -> "error";
            default -> "info";
        };
    }

    /**
     * @param since  only from this time (epoch ms; at most {@link #MAX_DAYS} days back)
     * @param before only keys below this (0 = up to now)
     * @param query  text that must appear (case does not matter), empty = all
     * @param levels levels to return, null or empty = all
     * @param count  also count the lines per level (reads the whole range)
     */
    Result find(LocalDate today, long since, long before, String query, Set<String> levels, int limit, boolean count,
                boolean links, boolean ips) throws IOException {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        boolean allLevels = levels == null || levels.isEmpty();
        LocalDate first = today.minusDays(MAX_DAYS - 1L);
        LocalDate from = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(Math.max(0, since)), zone);
        if (from.isBefore(first)) {
            from = first;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        LEVELS.forEach(l -> counts.put(l, 0));
        List<Line> found = new ArrayList<>();
        boolean more = false;
        days:
        for (LocalDate day = today; !day.isBefore(from); day = day.minusDays(1)) {
            if (!count && before > 0 && start(day) > before) {
                continue;
            }
            List<Line> lines = read(day, today, links, ips);
            for (int i = lines.size() - 1; i >= 0; i--) {
                Line line = lines.get(i);
                if (line.time() < since) {
                    break days;
                }
                if (!q.isEmpty() && !line.text().toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }
                if (count) {
                    counts.merge(line.level(), 1, Integer::sum);
                }
                if ((before > 0 && line.key() >= before) || (!allLevels && !levels.contains(line.level()))) {
                    continue;
                }
                if (found.size() == limit) {
                    more = true;
                    if (!count) {
                        break days;
                    }
                    continue;
                }
                found.add(line);
            }
        }
        return new Result(found, more, count ? counts : null);
    }

    /** All lines of one day in order: its .gz files by number, today also latest.log. */
    private List<Line> read(LocalDate day, LocalDate today, boolean links, boolean ips) throws IOException {
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(folder)) {
            try (Stream<Path> list = Files.list(folder)) {
                list.filter(f -> {
                    Matcher m = GZ.matcher(f.getFileName().toString());
                    return m.matches() && m.group(1).equals(day.toString());
                }).sorted(Comparator.comparingInt(f -> number(f.getFileName().toString()))).forEach(files::add);
            }
        }
        Path latest = folder.resolve("latest.log");
        if (day.equals(today) && Files.isRegularFile(latest)) {
            files.add(latest);
        }
        // Finished days do not change any more: kept (softly, the memory may take them back) for paging and searching.
        StringBuilder sig = new StringBuilder(folder + "|" + day + "|" + links + "|" + ips);
        for (Path file : files) {
            sig.append('|').append(file.getFileName()).append(':').append(Files.size(file));
        }
        String cacheKey = sig.toString();
        if (!day.equals(today)) {
            java.lang.ref.SoftReference<List<Line>> ref = DAYS.get(cacheKey);
            List<Line> cached = ref == null ? null : ref.get();
            if (cached != null) {
                return cached;
            }
        }
        long dayStart = start(day);
        List<Line> out = new ArrayList<>();
        long second = dayStart;
        int index = 0;
        String level = "info";
        for (Path file : files) {
            try (InputStream raw = Files.newInputStream(file);
                 InputStream in = file.getFileName().toString().endsWith(".gz") ? new GZIPInputStream(raw) : raw;
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = TIME.matcher(line);
                    if (m.find()) {
                        long t = dayStart + (Integer.parseInt(m.group(1)) * 3600L + Integer.parseInt(m.group(2)) * 60L
                                + Integer.parseInt(m.group(3))) * 1000L;
                        if (t != second) {
                            second = t;
                            index = 0;
                        }
                    }
                    String own = level(line);
                    if (own != null) {
                        level = own;
                    }
                    out.add(new Line(second + Math.min(index++, 999), second, level, clean(line, links, ips)));
                }
            } catch (IOException e) {
                // a broken or still written .gz - skip the rest of it
            }
        }
        if (!day.equals(today)) {
            DAYS.keySet().removeIf(k -> k.startsWith(folder + "|" + day + "|"));
            DAYS.put(cacheKey, new java.lang.ref.SoftReference<>(List.copyOf(out)));
        }
        return out;
    }

    /**
     * Every line from {@code since} up to (not including) {@code until} that contains the text, newest first - for
     * counting, the activity chart and one page of the page Log at once. {@code visit} returns false to stop.
     */
    public void scan(LocalDate today, long since, long until, String query, boolean links, boolean ips,
              java.util.function.Predicate<Line> visit) throws IOException {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        LocalDate first = today.minusDays(MAX_DAYS - 1L);
        LocalDate from = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(Math.max(0, since)), zone);
        if (from.isBefore(first)) {
            from = first;
        }
        for (LocalDate day = today; !day.isBefore(from); day = day.minusDays(1)) {
            if (start(day) >= until) {
                continue;
            }
            List<Line> lines = read(day, today, links, ips);
            for (int i = lines.size() - 1; i >= 0; i--) {
                Line line = lines.get(i);
                if (line.time() < since) {
                    return;
                }
                if (line.key() >= until || (!q.isEmpty() && !line.text().toLowerCase(Locale.ROOT).contains(q))) {
                    continue;
                }
                if (!visit.test(line)) {
                    return;
                }
            }
        }
    }

    private long start(LocalDate day) {
        return day.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    private static int number(String name) {
        Matcher m = GZ.matcher(name);
        return m.matches() ? Integer.parseInt(m.group(2)) : 0;
    }

    // ------------------------------------------------- the same as MRadmin's ConsoleLog

    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");
    private static final Pattern LINK = Pattern.compile("(?i)\\b(?:https?|ftp)://\\S+");
    private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?\\b");
    // Full IPv6 addresses and shortened ones with "::" - times like 12:34:56 do not match.
    private static final Pattern IPV6 = Pattern.compile(
            "(?i)\\b(?:[0-9a-f]{1,4}:){7}[0-9a-f]{1,4}\\b|(?:[0-9a-f]{1,4}:){0,6}:(?::[0-9a-f]{1,4}){1,7}|\\b(?:[0-9a-f]{1,4}:){1,7}:(?=[\\s\\]/%]|$)");

    /** Without colour codes, and with links / IP addresses hidden if asked (also for the older log files). */
    public static String clean(String line, boolean links, boolean ips) {
        String text = ANSI.matcher(line.replace("\r", "")).replaceAll("");
        if (links) {
            text = LINK.matcher(text).replaceAll("[Link]");
        }
        if (ips) {
            text = IPV4.matcher(text).replaceAll("[IP]");
            text = IPV6.matcher(text).replaceAll("[IP]");
        }
        return text;
    }
}
