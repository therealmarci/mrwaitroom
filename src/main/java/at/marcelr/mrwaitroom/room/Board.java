package at.marcelr.mrwaitroom.room;

import at.marcelr.mrwaitroom.MRwaitroom;
import at.marcelr.mrwaitroom.Messages;
import at.marcelr.mrwaitroom.link.MainLink;
import at.marcelr.mrwaitroom.link.MainStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.map.MapPalette;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The picture on the board ({@link Screen}): 4 x 2 maps = 512 x 256 pixels, drawn once per language and only again
 * when something on it changes (the countdown: once a second).
 * <pre>
 * ┌──────────────────────────────┬──────────────┐
 * │ NEUSTART                     │    1:42      │
 * │ Der Server startet neu.      │ verbleibend  │
 * │ Grund: Plugin-Updates        │ ▓▓▓▓▓░░░░░   │
 * ├──────────────────────────────┴──────────────┤
 * │ Keine Wartung geplant           BlockAttack │
 * └─────────────────────────────────────────────┘
 * </pre>
 * Texts: screen-&lt;state&gt; (title, text, detail, countdown, label) and screen-footer-* in the language files.
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

    /**
     * A drawn picture; {@code version} grows with every change, {@code tiles} is a fingerprint per map (row by row),
     * so only the maps whose part changed are sent again.
     */
    public record Picture(String content, Pixels pixels, long version, int[] tiles) {
    }

    /** Width of the progress bar - the bar moves pixel by pixel. */
    private static final int BAR = 116;

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
        Texts texts = m.in(language, () -> texts(m, line, view));
        // The dot next to the countdown blinks once a second while something counts down - the board looks alive.
        boolean pulse = !texts.time().isEmpty() && (now / 500) % 2 == 0;
        String content = texts + "|" + Math.round(line.progress() * BAR) + "|" + pulse;
        Picture old = pictures.get(language);
        if (old != null && old.content().equals(content)) {
            return old;
        }
        Pixels pixels = draw(texts, line.key(), line.progress(), pulse);
        Picture fresh = new Picture(content, pixels, ++versions, fingerprints(pixels));
        pictures.put(language, fresh);
        return fresh;
    }

    /** Forget all pictures (after a reload: other texts). */
    public void clear() {
        pictures.clear();
    }

    // ------------------------------------------------------------------- texts

    record Texts(String title, String text, String detail, String time, String label, String footer,
                         String server) {
    }

    private static Texts texts(Messages m, Display.Line line, MainLink.View view) {
        List<String> parts = m.lore("screen-" + line.key(), line.tags()).stream().map(Board::plain).toList();
        MainStatus status = view.status();
        MainStatus.Maintenance mt = status == null ? null : status.maintenance();
        String footer;
        if (line.key().startsWith("maintenance")) {
            footer = plain(m.get("screen-footer-maintenance", line.tags()));
        } else if (mt != null && mt.start() > now()) {
            footer = plain(m.get("screen-footer-planned", line.tags()));
        } else {
            footer = plain(m.get("screen-footer-none", line.tags()));
        }
        return new Texts(part(parts, 0), part(parts, 1), part(parts, 2), part(parts, 3), part(parts, 4), footer,
                plain(m.get("screen-footer-server", line.tags())));
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

    static Pixels draw(Texts t, String key, float progress, boolean pulse) {
        byte accent = accent(key);
        Pixels p = new Pixels(WIDTH, HEIGHT);
        p.fill(BACKGROUND);
        // Colour strip on the left, frame around everything.
        p.rect(0, 0, 8, HEIGHT, accent);
        p.frame(0, 0, WIDTH, HEIGHT, LINE);

        // Main area: state, text, detail.
        int left = 24;
        int mainWidth = 316;
        int titleScale = Pixels.fit(t.title(), mainWidth, 5);
        p.text(left, 18, t.title(), titleScale, accent);
        int y = 18 + Pixels.FONT_HEIGHT * titleScale + 16;
        for (String l : Pixels.wrap(t.text(), mainWidth, 2, 3)) {
            p.text(left, y, l, 2, WHITE);
            y += 20;
        }
        y += 6;
        for (String l : Pixels.wrap(t.detail(), mainWidth, 2, 2)) {
            if (y > 170) {
                break;
            }
            p.text(left, y, l, 2, GRAY);
            y += 20;
        }

        // Countdown panel on the right.
        int px = 352;
        int pw = 144;
        p.rect(px, 16, pw, 160, PANEL);
        p.frame(px, 16, pw, 160, LINE);
        int timeScale = Pixels.fit(t.time(), pw - 16, 5);
        int timeY = 60 - Pixels.FONT_HEIGHT * timeScale / 2;
        p.textCentered(px, pw, timeY, t.time(), timeScale, WHITE);
        List<String> label = Pixels.wrap(t.label(), pw - 12, 1, 2);
        for (int i = 0; i < label.size(); i++) {
            p.textCentered(px, pw, 104 + i * 11, label.get(i), 1, GRAY);
        }
        // Progress bar.
        int bx = px + 14;
        int bw = BAR;
        p.rect(bx, 140, bw, 16, DARK);
        p.rect(bx, 140, Math.round(bw * Math.clamp(progress, 0f, 1f)), 16, accent);
        p.frame(bx - 1, 139, bw + 2, 18, LINE);
        // Live dot in the corner of the panel.
        p.rect(px + pw - 14, 24, 6, 6, pulse ? accent : PANEL);

        // Footer: maintenance on the left, server name on the right.
        p.rect(8, 192, WIDTH - 8, 1, LINE);
        p.rect(8, 193, WIDTH - 9, HEIGHT - 194, PANEL);
        // The name gets smaller before the maintenance line is cut off.
        int room = WIDTH - 20 - left - 24 - Pixels.textWidth(t.footer(), 2);
        int serverScale = Pixels.fit(t.server(), Math.max(120, room), 3);
        int serverWidth = Pixels.textWidth(t.server(), serverScale);
        p.textRight(WIDTH - 20, 224 - Pixels.FONT_HEIGHT * serverScale / 2, t.server(), serverScale, WHITE);
        List<String> footer = Pixels.wrap(t.footer(), WIDTH - 20 - serverWidth - 24 - left, 2, 1);
        if (!footer.isEmpty()) {
            p.text(left, 216, footer.getFirst(), 2, GRAY);
        }
        return p;
    }
}
