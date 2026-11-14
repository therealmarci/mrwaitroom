package at.marcelr.mrwaitroom.room;

import at.marcelr.mrwaitroom.MRwaitroom;
import at.marcelr.mrwaitroom.Messages;
import at.marcelr.mrwaitroom.link.MainLink;
import at.marcelr.mrwaitroom.link.MainStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.map.MapPalette;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The picture on the board ({@link Screen}): 4 x 2 maps = 512 x 256 pixels, drawn once per language and only again
 * when something on it changes (the countdown and the clock: once a second).
 * <pre>
 * ┌────────────────────────────────────────────┐
 * │ NEUSTART                          1:42     │  head: state, text, reason, countdown
 * │ Der Server startet neu.       verbleibend  │
 * │ Grund: Plugin-Updates                      │
 * │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░░░ │  progress
 * ├──────────────────────────────┬─────────────┤
 * │ ━━ ── ──                     │    23:52    │  slides (like MRadmin's hero tile): maintenance,
 * │ NÄCHSTE WARTUNG              │  Sa. 14.11. │  one per warning, server infos - every 6 seconds
 * │ Umbau Spawn · 18:00 - 20:00  │   3 warten  │  clock, date, players waiting
 * └──────────────────────────────┴─────────────┘
 * </pre>
 * Texts: screen-&lt;state&gt; (title, text, detail, countdown, label) and screen-slide-*, screen-waiting* in the
 * language files.
 */
public final class Board {

    public static final int WIDTH = 512;
    public static final int HEIGHT = 256;

    @SuppressWarnings("removal")
    private static byte color(int r, int g, int b) {
        return MapPalette.matchColor(r, g, b);
    }

    private static final byte BACKGROUND = color(22, 22, 26);
    private static final byte PANEL = color(36, 36, 44);
    private static final byte LINE = color(70, 70, 80);
    private static final byte WHITE = color(240, 240, 240);
    private static final byte GRAY = color(150, 150, 150);
    private static final byte DARK = color(60, 60, 60);
    private static final byte YELLOW = color(230, 200, 60);
    private static final byte RED = color(220, 60, 60);
    private static final byte GOLD = color(230, 140, 40);
    private static final byte GREEN = color(90, 200, 90);
    private static final byte AQUA = color(80, 190, 220);
    private static final byte DARK_RED = color(90, 22, 22);

    /**
     * A drawn picture; {@code version} grows with every change, {@code tiles} is a fingerprint per map (row by row),
     * so only the maps whose part changed are sent again.
     */
    public record Picture(String content, Pixels pixels, long version, int[] tiles) {
    }

    /** Width of the progress bar - the bar moves pixel by pixel. */
    private static final int BAR = 480;
    /** Seconds per slide of the lower tile. */
    private static final int SLIDE_SECONDS = 6;

    private final MRwaitroom plugin;
    private final Map<String, Picture> pictures = new ConcurrentHashMap<>();
    private long versions;

    public Board(MRwaitroom plugin) {
        this.plugin = plugin;
    }

    /** The current picture in the language (drawn if missing). */
    public Picture picture(String language) {
        Picture p = pictures.get(language);
        return p != null ? p : refresh(language, plugin.link().view(), System.currentTimeMillis());
    }

    /** Draws the picture of every language that is shown again - if its content changed. Main thread, 4 times a second. */
    public void refresh(Iterable<String> languages, MainLink.View view, long now) {
        for (String language : languages) {
            refresh(language, view, now);
        }
    }

    synchronized Picture refresh(String language, MainLink.View view, long now) {
        Messages m = plugin.messages();
        Display.Line line = plugin.display().line(view, now);
        Texts texts = m.in(language, () -> texts(m, line, view, language, now));
        // The dot next to the countdown blinks once a second while something counts down - the board looks alive.
        boolean pulse = !texts.time().isEmpty() && (now / 500) % 2 == 0;
        // Which slide is on and how far (whole seconds - the segment fills once a second, not on every frame).
        int slide = texts.slides().isEmpty() ? 0 : (int) ((now / 1000 / SLIDE_SECONDS) % texts.slides().size());
        int step = (int) ((now / 1000) % SLIDE_SECONDS) + 1;
        String content = texts + "|" + Math.round(line.progress() * BAR) + "|" + pulse + "|" + slide + "|" + step;
        Picture old = pictures.get(language);
        if (old != null && old.content().equals(content)) {
            return old;
        }
        Pixels pixels = draw(texts, line.key(), line.progress(), pulse, slide, step);
        Picture fresh = new Picture(content, pixels, ++versions, fingerprints(pixels));
        pictures.put(language, fresh);
        return fresh;
    }

    /** Forget all pictures (after a reload: other texts). */
    public void clear() {
        pictures.clear();
    }

    // ------------------------------------------------------------------- texts

    /** One slide of the lower tile: a small coloured heading and up to three lines. */
    record Slide(String kind, String color, List<String> lines) {
    }

    /** alerts: "2 Warnungen" while warnings are on, else empty. */
    record Texts(String title, String text, String detail, String time, String label, List<Slide> slides,
                 String clock, String date, String waiting, String alerts) {
    }

    private Texts texts(Messages m, Display.Line line, MainLink.View view, String language, long now) {
        List<String> parts = m.lore("screen-" + line.key(), line.tags()).stream().map(Board::plain).toList();
        MainStatus status = view.status();
        MainStatus.Maintenance mt = status == null ? null : status.maintenance();
        List<Slide> slides = new ArrayList<>();
        // Warnings first (one slide each) - they matter most.
        int alertCount = 0;
        if (status != null) {
            for (Map<String, String> alert : status.alerts()) {
                slides.add(new Slide(plain(m.get("screen-slide-alert")), "red", List.of(MainStatus.in(alert, language))));
                alertCount++;
            }
        }
        // Maintenance: the running one (its countdown is in the head then) or the next one.
        if (mt != null && (mt.active() || mt.start() > now)) {
            String when = plain(m.get(mt.end() > 0 ? "screen-slide-maintenance-when" : "screen-slide-maintenance-open", line.tags()));
            slides.add(new Slide(plain(m.get(mt.active() ? "screen-slide-maintenance-active" : "screen-slide-maintenance-next")),
                    "aqua", List.of(plain(m.get("screen-slide-maintenance-title", line.tags())), when)));
        }
        // Server infos: always there.
        boolean down = view.downSince() > 0;
        slides.add(new Slide(plain(m.get("screen-slide-server")), "white", List.of(
                plain(m.get("screen-slide-server-name", line.tags())),
                plain(m.get(down ? "screen-slide-down" : "screen-slide-up", line.tags())),
                plain(m.get("screen-slide-estimate", line.tags())))));
        ZonedDateTime time = Instant.ofEpochMilli(now).atZone(plugin.settings().zone());
        Locale locale = Locale.forLanguageTag(language);
        int waiting = org.bukkit.Bukkit.getOnlinePlayers().size();
        return new Texts(part(parts, 0), part(parts, 1), part(parts, 2), part(parts, 3), part(parts, 4), List.copyOf(slides),
                DateTimeFormatter.ofPattern("HH:mm").format(time), DateTimeFormatter.ofPattern("EE dd.MM.", locale).format(time),
                plain(m.get(waiting == 1 ? "screen-waiting-one" : "screen-waiting", Messages.tag("count", String.valueOf(waiting)))),
                alertCount == 0 ? "" : plain(m.get(alertCount == 1 ? "screen-alerts-one" : "screen-alerts",
                        Messages.tag("count", String.valueOf(alertCount)))));
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static String part(List<String> parts, int index) {
        return index < parts.size() ? parts.get(index) : "";
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c).strip();
    }

    // -------------------------------------------------------------------- draw

    private static byte accent(String key) {
        if (key.startsWith("restart")) {
            return YELLOW;
        }
        if (key.startsWith("stop")) {
            return RED;
        }
        if (key.startsWith("crash")) {
            return GOLD;
        }
        if (key.startsWith("maintenance")) {
            return AQUA;
        }
        return GREEN;
    }

    /** One fingerprint per map (128 x 128), row by row. */
    static int[] fingerprints(Pixels p) {
        int[] tiles = new int[(WIDTH / 128) * (HEIGHT / 128)];
        for (int i = 0; i < tiles.length; i++) {
            int ox = (i % (WIDTH / 128)) * 128;
            int oy = (i / (WIDTH / 128)) * 128;
            int h = 1;
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    h = 31 * h + p.get(ox + x, oy + y);
                }
            }
            tiles[i] = h;
        }
        return tiles;
    }

    private static byte color(String name) {
        return switch (name) {
            case "aqua" -> AQUA;
            case "red" -> RED;
            case "gold" -> GOLD;
            case "yellow" -> YELLOW;
            case "green" -> GREEN;
            default -> WHITE;
        };
    }

    static Pixels draw(Texts t, String key, float progress, boolean pulse, int slide, int step) {
        byte accent = accent(key);
        Pixels p = new Pixels(WIDTH, HEIGHT);
        p.fill(BACKGROUND);
        p.frame(0, 0, WIDTH, HEIGHT, LINE);

        // ---- Head: colour strip, state, text, reason - the countdown on the right.
        p.rect(0, 0, 6, HEIGHT, accent);
        int left = 20;
        int countWidth = 150;
        int mainWidth = WIDTH - left - countWidth - 24;
        int titleScale = Pixels.fit(t.title(), mainWidth, 5);
        p.text(left, 14, t.title(), titleScale, accent);
        int y = 14 + Pixels.FONT_HEIGHT * titleScale + 10;
        for (String l : Pixels.wrap(t.text(), mainWidth, 2, 2)) {
            p.text(left, y, l, 2, WHITE);
            y += 19;
        }
        for (String l : Pixels.wrap(t.detail(), mainWidth, 2, 1)) {
            if (y <= 92) {
                p.text(left, y, l, 2, GRAY);
            }
        }
        int cx = WIDTH - countWidth - 12;
        int timeScale = Pixels.fit(t.time(), countWidth, 6);
        p.textCentered(cx, countWidth, 46 - Pixels.FONT_HEIGHT * timeScale / 2, t.time(), timeScale, WHITE);
        List<String> label = Pixels.wrap(t.label(), countWidth, 1, 1);
        if (!label.isEmpty()) {
            p.textCentered(cx, countWidth, 82, label.getFirst(), 1, GRAY);
        }
        // Live dot while something counts down.
        p.rect(WIDTH - 16, 10, 6, 6, pulse ? accent : BACKGROUND);
        // Progress over the whole width.
        int bx = 16;
        p.rect(bx, 108, BAR, 10, DARK);
        p.rect(bx, 108, Math.round(BAR * Math.clamp(progress, 0f, 1f)), 10, accent);
        p.frame(bx - 1, 107, BAR + 2, 12, LINE);

        // ---- Lower left: the slides (like MRadmin's hero tile).
        int ty = 134;
        int th = HEIGHT - ty - 8;
        int sw = 352;
        p.rect(8, ty, sw, th, PANEL);
        p.frame(8, ty, sw, th, LINE);
        if (!t.slides().isEmpty()) {
            Slide current = t.slides().get(Math.min(slide, t.slides().size() - 1));
            byte tone = color(current.color());
            boolean alert = current.color().equals("red");
            if (alert) {
                // A warning: dark red tile, thick red frame - it has to stand out.
                p.rect(8, ty, sw, th, DARK_RED);
                p.frame(8, ty, sw, th, RED);
                p.frame(9, ty + 1, sw - 2, th - 2, RED);
            }
            // Segments: one per slide, the current one fills up second by second.
            int n = t.slides().size();
            int gap = 4;
            int segWidth = (sw - 24 - gap * (n - 1)) / n;
            for (int i = 0; i < n; i++) {
                int sx = 20 + i * (segWidth + gap);
                p.rect(sx, ty + 10, segWidth, 3, DARK);
                int fill = i < slide ? segWidth : i == slide ? segWidth * step / SLIDE_SECONDS : 0;
                p.rect(sx, ty + 10, fill, 3, i == slide ? tone : GRAY);
            }
            int ly;
            if (alert) {
                // "!" badge and a big heading
                p.rect(20, ty + 20, 16, 16, RED);
                p.text(26, ty + 24, "!", 1, WHITE);
                p.text(44, ty + 20, current.kind(), 2, WHITE);
                ly = ty + 46;
            } else {
                p.text(20, ty + 22, current.kind(), 1, tone);
                ly = ty + 38;
            }
            int lineWidth = sw - 32;
            boolean first = true;
            for (String l : current.lines()) {
                int scale = first ? 2 : 1;
                for (String w : Pixels.wrap(l, lineWidth, scale, first && current.lines().size() == 1 ? (alert ? 2 : 3) : 1)) {
                    if (ly + Pixels.FONT_HEIGHT * scale > ty + th - 6) {
                        break;
                    }
                    p.text(20, ly, w, scale, first ? WHITE : alert ? WHITE : GRAY);
                    ly += Pixels.FONT_HEIGHT * scale + (first ? 8 : 6);
                }
                first = false;
            }
        }

        // ---- Lower right: clock, date, players waiting.
        int rx = 8 + sw + 8;
        int rw = WIDTH - rx - 8;
        p.rect(rx, ty, rw, th, PANEL);
        p.frame(rx, ty, rw, th, LINE);
        int top = ty + 18;
        if (!t.alerts().isEmpty()) {
            p.rect(rx, ty, rw, 18, RED);
            p.textCentered(rx, rw, ty + 5, t.alerts(), 1, WHITE);
            top = ty + 26;
        }
        int clockScale = Pixels.fit(t.clock(), rw - 16, t.alerts().isEmpty() ? 4 : 3);
        p.textCentered(rx, rw, top, t.clock(), clockScale, WHITE);
        p.textCentered(rx, rw, top + Pixels.FONT_HEIGHT * clockScale + 8, t.date(), 1, GRAY);
        p.textCentered(rx, rw, ty + th - 26, t.waiting(), Pixels.fit(t.waiting(), rw - 16, 2), accent);
        return p;
    }
}
