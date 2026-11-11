package at.marcelr.mrwaitroom.room;

import org.bukkit.map.MapFont;
import org.bukkit.map.MinecraftFont;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A picture in map colours (one byte per pixel, see MapPalette) with the Minecraft font, scaled in whole steps, so
 * the board stays pixel-sharp. Without Java2D fonts on purpose: servers often run without any fonts installed.
 */
public final class Pixels {

    /** Height of one line of the font at scale 1. */
    public static final int FONT_HEIGHT = 8;

    private final int width;
    private final int height;
    private final byte[] data;

    public Pixels(int width, int height) {
        this.width = width;
        this.height = height;
        this.data = new byte[width * height];
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public byte get(int x, int y) {
        return data[y * width + x];
    }

    public byte[] data() {
        return data;
    }

    public void fill(byte color) {
        Arrays.fill(data, color);
    }

    public void rect(int x, int y, int w, int h, byte color) {
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(width, x + w);
        int y1 = Math.min(height, y + h);
        for (int yy = y0; yy < y1; yy++) {
            Arrays.fill(data, yy * width + x0, Math.max(yy * width + x0, yy * width + x1), color);
        }
    }

    /** A one pixel frame around the area. */
    public void frame(int x, int y, int w, int h, byte color) {
        rect(x, y, w, 1, color);
        rect(x, y + h - 1, w, 1, color);
        rect(x, y, 1, h, color);
        rect(x + w - 1, y, 1, h, color);
    }

    // -------------------------------------------------------------------- text

    /**
     * Umlauts and ß: Bukkit's MinecraftFont only has usable shapes for ASCII (everything else is garbled), so these
     * are put together here - the base letter with two dots above, ß drawn by hand.
     */
    private static final Map<Character, boolean[][]> EXTRA = new HashMap<>();

    static {
        EXTRA.put('ä', dotted('a', false));
        EXTRA.put('ö', dotted('o', false));
        EXTRA.put('ü', dotted('u', false));
        EXTRA.put('Ä', dotted('A', true));
        EXTRA.put('Ö', dotted('O', true));
        EXTRA.put('Ü', dotted('U', true));
        EXTRA.put('ß', shape(".##.", "#..#", "#.#.", "#..#", "#..#", "#.#."));
    }

    /** The shape of a character: rows of pixels, all rows of the same width. */
    static boolean[][] glyph(char c) {
        boolean[][] extra = EXTRA.get(c);
        if (extra != null) {
            return extra;
        }
        MapFont.CharacterSprite sprite = MinecraftFont.Font.getChar(c);
        boolean[][] g = new boolean[FONT_HEIGHT][Math.max(1, sprite.getWidth())];
        for (int row = 0; row < Math.min(FONT_HEIGHT, sprite.getHeight()); row++) {
            for (int col = 0; col < sprite.getWidth(); col++) {
                g[row][col] = sprite.get(row, col);
            }
        }
        return g;
    }

    /** A letter with two dots above (capitals get shorter by two rows to make room, like in Minecraft). */
    private static boolean[][] dotted(char base, boolean capital) {
        boolean[][] b = glyph(base);
        int w = b[0].length;
        boolean[][] g = new boolean[FONT_HEIGHT][w];
        g[0][1] = true;
        g[0][w - 2] = true;
        if (capital) {
            int[] keep = {0, 2, 3, 5, 6};
            for (int i = 0; i < keep.length; i++) {
                g[2 + i] = b[keep[i]].clone();
            }
        } else {
            for (int row = 2; row < FONT_HEIGHT; row++) {
                g[row] = b[row].clone();
            }
        }
        return g;
    }

    private static boolean[][] shape(String... rows) {
        boolean[][] g = new boolean[FONT_HEIGHT][rows[0].length()];
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < rows[row].length(); col++) {
                g[row + 1][col] = rows[row].charAt(col) == '#';
            }
        }
        return g;
    }

    /** Width of the text in pixels (1 pixel space between characters, all scaled). */
    public static int textWidth(String text, int scale) {
        String t = printable(text);
        int w = 0;
        for (int i = 0; i < t.length(); i++) {
            w += glyph(t.charAt(i))[0].length + 1;
        }
        return Math.max(0, w - 1) * scale;
    }

    /** Draws the text with its top left corner at x/y. */
    public void text(int x, int y, String text, int scale, byte color) {
        String t = printable(text);
        int cx = x;
        for (int i = 0; i < t.length(); i++) {
            boolean[][] g = glyph(t.charAt(i));
            for (int row = 0; row < g.length; row++) {
                for (int col = 0; col < g[row].length; col++) {
                    if (g[row][col]) {
                        rect(cx + col * scale, y + row * scale, scale, scale, color);
                    }
                }
            }
            cx += (g[0].length + 1) * scale;
        }
    }

    /** Draws the text centred in the area from x with width w. */
    public void textCentered(int x, int w, int y, String text, int scale, byte color) {
        text(x + (w - textWidth(text, scale)) / 2, y, text, scale, color);
    }

    /** Draws the text so it ends at x. */
    public void textRight(int x, int y, String text, int scale, byte color) {
        text(x - textWidth(text, scale), y, text, scale, color);
    }

    /** The largest scale (from max down to 1) at which the text fits into the width. */
    public static int fit(String text, int maxWidth, int max) {
        for (int scale = max; scale > 1; scale--) {
            if (textWidth(text, scale) <= maxWidth) {
                return scale;
            }
        }
        return 1;
    }

    /**
     * Breaks the text into lines that fit into the width - at most {@code maxLines}, the last one shortened with "..."
     * if there is more.
     */
    public static List<String> wrap(String text, int maxWidth, int scale, int maxLines) {
        List<String> lines = new ArrayList<>();
        String rest = printable(text).strip();
        while (!rest.isEmpty() && lines.size() < maxLines) {
            if (textWidth(rest, scale) <= maxWidth) {
                lines.add(rest);
                rest = "";
                break;
            }
            int cut = rest.length();
            while (cut > 1 && textWidth(rest.substring(0, cut), scale) > maxWidth) {
                cut--;
            }
            int space = rest.lastIndexOf(' ', cut);
            int end = space > 0 ? space : cut;
            lines.add(rest.substring(0, end).strip());
            rest = rest.substring(end).strip();
        }
        if (!rest.isEmpty() && !lines.isEmpty()) {
            String last = lines.removeLast();
            while (!last.isEmpty() && textWidth(last + "...", scale) > maxWidth) {
                last = last.substring(0, last.length() - 1);
            }
            lines.add(last.strip() + "...");
        }
        return lines;
    }

    /** Characters the Minecraft font does not have are replaced (dashes, arrows, ...) or dropped. */
    static String printable(String text) {
        StringBuilder b = new StringBuilder();
        for (char c : text.toCharArray()) {
            String replaced = switch (c) {
                case '–', '—', '‑' -> "-";
                case '…' -> "...";
                case '→', '➜' -> ">";
                case '•', '·' -> "*";
                case '„', '“', '”' -> "\"";
                case '‚', '‘', '’' -> "'";
                default -> null;
            };
            if (replaced != null) {
                b.append(replaced);
            } else if (EXTRA.containsKey(c) || (c >= 32 && c < 127 && MinecraftFont.Font.getChar(c) != null)) {
                b.append(c);
            }
        }
        return b.toString();
    }
}
