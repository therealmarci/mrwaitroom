package at.marcelr.mrwaitroom;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/** All values from config.yml, parsed and validated once per (re)load. */
public record Settings(
        ZoneId zone,
        MainSettings main,
        ReturnSettings back,
        RoomSettings room,
        DisplaySettings display,
        XaeroSettings xaero,
        UpdateSettings update,
        ServerSettings server
) {

    /** Section server: starter settings and planned restarts (only with MRadmin's starter). */
    public record ServerSettings(String memoryMin, String memoryMax, String javaFlags, String paperUpdate,
                                 List<LocalTime> restartTimes, String plainStop, int hangSeconds) {
    }

    /**
     * @param server   name of the main server in the proxy
     * @param host     host of the main server (for the "is it up" check)
     * @param port     Minecraft port of the main server
     * @param linkUrl  MRadmin's waiting room endpoint, "" = none
     */
    public record MainSettings(String server, String host, int port, String linkUrl, String linkKey, int pollSeconds) {
    }

    public record ReturnSettings(int delaySeconds, int batchSize, int batchSeconds, int maxAttempts,
                                 int estimateSeconds) {
    }

    public record RoomSettings(String world, boolean platform, Material platformBlock, boolean chat,
                               boolean hidePlayers) {
    }

    public record DisplaySettings(Material item, int slot, boolean bossbar, BossBar.Color bossbarColor) {
    }

    public record XaeroSettings(boolean enabled, int worldId) {
    }

    /** @param restartWhenIdle restart for an update from MRadmin as soon as nobody waits and the main server is ready */
    public record UpdateSettings(boolean enabled, String source, boolean includePrereleases,
                                 int intervalHours, boolean notifyOnJoin, boolean restartWhenIdle) {
    }

    public static Settings from(FileConfiguration c, Logger log) {
        ZoneId zone = ZoneId.systemDefault();
        String rawZone = c.getString("general.timezone", "").trim();
        if (!rawZone.isEmpty()) {
            try {
                zone = ZoneId.of(rawZone);
            } catch (DateTimeException e) {
                log.warning("general.timezone '" + rawZone + "' is unknown, using the time zone of this machine.");
            }
        }

        String address = c.getString("main.address", "127.0.0.1:25565").trim();
        String host = address;
        int port = 25565;
        int colon = address.lastIndexOf(':');
        if (colon > 0 && !address.endsWith("]")) {
            host = address.substring(0, colon);
            try {
                port = Math.clamp(Integer.parseInt(address.substring(colon + 1)), 1, 65535);
            } catch (NumberFormatException e) {
                log.warning("main.address '" + address + "' has no valid port, using 25565.");
            }
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        String linkUrl = c.getString("main.link-url", "").trim();
        while (linkUrl.endsWith("/")) {
            linkUrl = linkUrl.substring(0, linkUrl.length() - 1);
        }
        if (!linkUrl.isEmpty() && !linkUrl.startsWith("http://") && !linkUrl.startsWith("https://")) {
            log.warning("main.link-url must start with http:// or https:// - the waiting room works without MRadmin.");
            linkUrl = "";
        }
        String server = c.getString("main.server", "main").trim();
        MainSettings main = new MainSettings(server.isEmpty() ? "main" : server, host, port, linkUrl,
                c.getString("main.link-key", "").trim(), Math.clamp(c.getInt("main.poll-seconds", 2), 1, 30));

        ReturnSettings back = new ReturnSettings(
                Math.clamp(c.getInt("return.delay-seconds", 10), 0, 300),
                Math.clamp(c.getInt("return.batch-size", 5), 1, 100),
                Math.clamp(c.getInt("return.batch-seconds", 2), 1, 60),
                Math.clamp(c.getInt("return.max-attempts", 3), 1, 20),
                Math.clamp(c.getInt("return.estimate-seconds", 120), 10, 3600));

        String world = c.getString("room.world", "waitroom").trim();
        RoomSettings room = new RoomSettings(world.isEmpty() ? "waitroom" : world, c.getBoolean("room.platform", true),
                material(c.getString("room.platform-block", "SMOOTH_STONE"), Material.SMOOTH_STONE, true,
                        "room.platform-block", log),
                c.getBoolean("room.chat", false), c.getBoolean("room.hide-players", false));

        BossBar.Color color = BossBar.Color.BLUE;
        String rawColor = c.getString("display.bossbar-color", "BLUE").trim().toUpperCase(Locale.ROOT);
        try {
            color = BossBar.Color.valueOf(rawColor);
        } catch (IllegalArgumentException e) {
            log.warning("display.bossbar-color '" + rawColor + "' is unknown, using BLUE.");
        }
        DisplaySettings display = new DisplaySettings(
                material(c.getString("display.item", "CLOCK"), Material.CLOCK, false, "display.item", log),
                Math.clamp(c.getInt("display.slot", 4), 0, 8), c.getBoolean("display.bossbar", true), color);

        XaeroSettings xaero = new XaeroSettings(c.getBoolean("xaero.enabled", true), c.getInt("xaero.world-id", 0));

        UpdateSettings update = new UpdateSettings(
                c.getBoolean("updates.enabled", true),
                c.getString("updates.source", "github"),
                c.getBoolean("updates.beta", false),
                Math.max(1, c.getInt("updates.interval-hours", 6)),
                c.getBoolean("updates.notify-on-join", true),
                c.getBoolean("updates.restart-when-idle", true));

        List<LocalTime> times = new ArrayList<>();
        for (Object raw : c.getList("server.restart-times", List.of())) {
            LocalTime t = time(raw);
            if (t == null) {
                log.warning("server.restart-times: '" + raw + "' is not a time like \"05:30\" - ignored.");
            } else if (!times.contains(t)) {
                times.add(t);
            }
        }
        String paper = c.getString("server.paper-update", "auto").trim().toLowerCase(Locale.ROOT);
        ServerSettings serverSettings = new ServerSettings(memory(c.getString("server.memory-min", "1G"), "1G"),
                memory(c.getString("server.memory-max", "2G"), "2G"), c.getString("server.java-flags", "").trim(),
                paper.matches("auto|notify|off") ? paper : "auto", List.copyOf(times),
                "restart".equalsIgnoreCase(c.getString("server.plain-stop", "stop").trim()) ? "restart" : "stop",
                Math.clamp(c.getInt("server.hang-seconds", 180), 0, 3600));

        return new Settings(zone, main, back, room, display, xaero, update, serverSettings);
    }

    /** "05:30", or a number: YAML 1.1 reads an unquoted 5:30 as 330 (minutes) - counted back. */
    static LocalTime time(Object raw) {
        if (raw instanceof Number n && n.intValue() >= 0 && n.intValue() < 24 * 60) {
            return LocalTime.of(n.intValue() / 60, n.intValue() % 60);
        }
        String s = String.valueOf(raw).trim();
        if (!s.matches("\\d{1,2}:\\d{2}")) {
            return null;
        }
        String[] p = s.split(":");
        int h = Integer.parseInt(p[0]);
        int m = Integer.parseInt(p[1]);
        return h < 24 && m < 60 ? LocalTime.of(h, m) : null;
    }

    /** "2G", "1024M" - otherwise the fallback. */
    private static String memory(String raw, String fallback) {
        String s = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return s.matches("\\d{1,6}[MG]") ? s : fallback;
    }

    private static Material material(String raw, Material fallback, boolean block, String key, Logger log) {
        Material m = Material.matchMaterial(raw == null ? "" : raw.trim());
        if (m == null || m.isAir() || (block ? !m.isBlock() : !m.isItem())) {
            log.warning(key + " '" + raw + "' is not a valid " + (block ? "block" : "item") + ", using " + fallback + ".");
            return fallback;
        }
        return m;
    }
}
