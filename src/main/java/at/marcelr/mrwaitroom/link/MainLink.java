package at.marcelr.mrwaitroom.link;

import at.marcelr.mrwaitroom.MRwaitroom;
import at.marcelr.mrwaitroom.Settings;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Watches the main server: every few seconds (main.poll-seconds) it checks whether the Minecraft port answers and
 * asks MRadmin there (main.link-url) what is going on. The last answer of MRadmin is kept - also on disk
 * (state.yml) - so the waiting room still knows the reason of a restart or the end of a maintenance while the main
 * server is down. It also measures how long restarts take, for the estimate of the waiting time.
 * <p>
 * Polls on its own thread; everything the rest of the plugin reads is a consistent snapshot ({@link #view()}).
 */
public final class MainLink {

    /** What is going on with the main server, from the waiting room's point of view. */
    public enum Phase {
        /** Down for a restart (announced) - it comes back. */
        RESTART,
        /** Stopped on purpose - it stays down for now. */
        STOP,
        /** Down without announcement (crash, or MRadmin was not reachable). */
        CRASH,
        /** The port answers again, but the server is not ready yet. */
        STARTING,
        /** Up, but in maintenance - only players with the bypass go back. */
        MAINTENANCE,
        /** Up and ready - players go back. */
        READY
    }

    /**
     * Everything at one moment.
     *
     * @param status     last answer of MRadmin (kept while the main server is down), null if never one
     * @param downSince  since when the main server is down (epoch ms), 0 if it is up
     * @param readySince since when it is ready (epoch ms), 0 if it is not
     * @param estimate   expected length of a restart (ms)
     * @param linkOk     the last question to MRadmin got an answer
     * @param linkError  why the last question failed ("" if it did not)
     * @param checkedAt  when the main server was last checked (epoch ms)
     */
    public record View(Phase phase, MainStatus status, long downSince, long readySince, long estimate, boolean linkOk,
                       boolean portOpen, String linkError, long checkedAt) {
    }

    private static final int MAX_DURATIONS = 10;
    /** A downtime longer than this was no ordinary restart and does not count for the estimate. */
    private static final long MAX_RESTART = 60 * 60_000L;

    private final MRwaitroom plugin;
    private final File file;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private BukkitTask task;

    // Guarded by this.
    private MainStatus status;
    private long downSince;
    /** restart, stop, crash - or unknown (down since the waiting room started, no answer before). */
    private String downKind = "unknown";
    private long readySince;
    private final List<Long> durations = new ArrayList<>();
    private boolean up = true;
    private boolean ready;
    private boolean linkOk;
    private boolean portOpen;
    private String linkError = "";
    private volatile View view;
    /** Phase of the last round (guarded by this). */
    private Phase lastPhase = Phase.CRASH;

    public MainLink(MRwaitroom plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "state.yml");
        this.view = new View(Phase.CRASH, null, System.currentTimeMillis(), 0, 90_000L, false, false, "", 0);
    }

    public View view() {
        return view;
    }

    public void start() {
        stop();
        load();
        long period = plugin.settings().main().pollSeconds() * 20L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::poll, 1L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Asks right now (for /mrwaitroom status), on the calling thread - never call it from the server thread. */
    public void poll() {
        Settings.MainSettings s = plugin.settings().main();
        boolean open = portOpen(s.host(), s.port());
        MainStatus fresh = null;
        String error = "";
        if (!s.linkUrl().isEmpty()) {
            try {
                fresh = fetch(s);
            } catch (IOException | RuntimeException e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        update(open, fresh, error, !s.linkUrl().isEmpty(), System.currentTimeMillis());
    }

    private MainStatus fetch(Settings.MainSettings s) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(s.linkUrl() + "/waiting-room/status"))
                .timeout(Duration.ofSeconds(3))
                .header("Authorization", "Bearer " + s.linkKey())
                .header("User-Agent", "MRwaitroom")
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401) {
            throw new IOException("wrong key (main.link-key)");
        }
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        // Orders for the management from MRadmin (configuration, updates) come with the answer.
        var json = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
        plugin.remote().take(json.get("orders"));
        return MainStatus.parse(response.body());
    }

    private static boolean portOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** One round: what was seen, and what follows from it. Package-private for the tests. */
    synchronized void update(boolean open, MainStatus fresh, String error, boolean withLink, long now) {
        boolean changed = false;
        boolean wasLinkOk = linkOk;
        linkOk = fresh != null;
        portOpen = open;
        linkError = error;
        if (fresh != null) {
            // While the main server is still starting MRadmin may not know the maintenance yet (it answers early in
            // the start): the running maintenance is kept until the server is ready - otherwise the display jumps
            // between "starting" and "maintenance".
            if (!fresh.ready() && !fresh.maintenanceActive() && maintenance(status, now)) {
                fresh = fresh.withMaintenance(status.maintenance());
            }
            status = fresh;
            changed = true;
        }
        if (withLink && wasLinkOk && !linkOk && open) {
            plugin.getLogger().warning("MRadmin on the main server does not answer (" + error + ").");
        }
        // Up = the port answers (or MRadmin does); while MRadmin says it is going down it counts as down.
        boolean isUp = (open || fresh != null) && !(fresh != null && fresh.stopping());
        // Ready = MRadmin says so; without MRadmin: the port answers.
        boolean isReady;
        if (!isUp) {
            isReady = false;
        } else if (fresh != null) {
            isReady = fresh.ready();
        } else if (!withLink) {
            isReady = true;
        } else {
            // The port answers but MRadmin does not: still starting - or MRadmin is broken or the key is wrong. Then
            // after 5 minutes it counts as ready anyway, so nobody waits forever.
            isReady = downSince == 0 || now - downSince > 5 * 60_000L;
        }
        if (!isUp) {
            if (downSince == 0) {
                downSince = now;
                downKind = kindOf(status, now);
                changed = true;
                plugin.getLogger().info("Main server is down (" + downKind + ").");
            }
            readySince = 0;
        } else if (isReady) {
            if (downSince != 0) {
                long took = now - downSince;
                // Only real restarts and crashes count - not a stop, and not the time before the first answer.
                if ((downKind.equals("restart") || downKind.equals("crash")) && took > 0 && took < MAX_RESTART) {
                    durations.add(took);
                    while (durations.size() > MAX_DURATIONS) {
                        durations.removeFirst();
                    }
                }
                if (!downKind.equals("unknown")) {
                    plugin.getLogger().info("Main server is ready again after " + (took / 1000) + " s.");
                }
                downSince = 0;
                changed = true;
            }
            if (readySince == 0) {
                readySince = now;
            }
        } else {
            readySince = 0;
        }
        up = isUp;
        ready = isReady;
        Phase phase = phase(now);
        // A maintenance ended (also early) while the main server was up: the countdown before going back starts now,
        // not when the server became ready long before.
        if (phase == Phase.READY && lastPhase == Phase.MAINTENANCE && readySince != 0) {
            readySince = now;
        }
        lastPhase = phase;
        view = new View(phase, status, downSince, readySince, estimate(), linkOk, portOpen, linkError, now);
        if (changed) {
            save();
        }
    }

    private Phase phase(long now) {
        // During a maintenance it stays "maintenance" - also while the main server restarts or is off meanwhile
        // (that is part of the maintenance). Once its end has passed, the normal display applies again.
        if (maintenance(status, now)) {
            return Phase.MAINTENANCE;
        }
        if (ready) {
            return Phase.READY;
        }
        if (up) {
            return Phase.STARTING;
        }
        return switch (downKind) {
            case "restart" -> Phase.RESTART;
            case "stop" -> Phase.STOP;
            default -> Phase.CRASH;
        };
    }

    /** A maintenance runs (as last reported) and its end has not passed. */
    static boolean maintenance(MainStatus status, long now) {
        return status != null && status.maintenanceActive()
                && (status.maintenance().end() <= 0 || status.maintenance().end() > now);
    }

    /** Why the main server went down, from the last answer before. */
    static String kindOf(MainStatus last, long now) {
        if (last == null || last.restart() == null) {
            return "crash";
        }
        // A countdown that ended (or is about to) - otherwise the server went down without it.
        if (!last.stopping() && last.restart().at() > now + 30_000L) {
            return "crash";
        }
        return last.restart().restart() ? "restart" : "stop";
    }

    /** Length of the countdown of a restart: return.estimate-seconds (the server may well be back earlier). */
    long estimate() {
        return plugin.settings().back().estimateSeconds() * 1000L;
    }

    // --------------------------------------------------------------- state.yml

    private synchronized void load() {
        if (!file.exists()) {
            downSince = System.currentTimeMillis();
            downKind = "unknown";
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String json = yaml.getString("last-status", "");
        if (!json.isEmpty()) {
            try {
                status = MainStatus.parse(json);
            } catch (RuntimeException e) {
                status = null;
            }
        }
        downSince = yaml.getLong("down-since", 0);
        downKind = yaml.getString("down-kind", "unknown");
        durations.clear();
        for (long d : yaml.getLongList("restart-durations")) {
            if (d > 0 && d < MAX_RESTART) {
                durations.add(d);
            }
        }
        if (downSince == 0) {
            // Until the first answer the main server counts as down (since now).
            downSince = System.currentTimeMillis();
            downKind = "unknown";
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("Kept by MRwaitroom - what the main server reported last. Do not edit."));
        yaml.set("last-status", status == null ? "" : json(status));
        yaml.set("down-since", downSince);
        yaml.set("down-kind", downKind);
        yaml.set("restart-durations", List.copyOf(durations));
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save state.yml: " + e.getMessage());
        }
    }

    /** Back to the JSON MRadmin sends, so state.yml is read the same way. */
    private static String json(MainStatus s) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("time", s.time());
        o.addProperty("name", s.name());
        o.addProperty("ready", s.ready());
        o.addProperty("stopping", s.stopping());
        if (s.restart() != null) {
            com.google.gson.JsonObject r = new com.google.gson.JsonObject();
            r.addProperty("at", s.restart().at());
            r.addProperty("restart", s.restart().restart());
            r.addProperty("reason", s.restart().reason());
            o.add("restart", r);
        }
        if (s.maintenance() != null) {
            com.google.gson.JsonObject m = new com.google.gson.JsonObject();
            m.addProperty("active", s.maintenance().active());
            m.addProperty("title", s.maintenance().title());
            m.addProperty("start", s.maintenance().start());
            m.addProperty("end", s.maintenance().end());
            m.addProperty("bypass", s.maintenance().bypass());
            o.add("maintenance", m);
        }
        com.google.gson.JsonArray alerts = new com.google.gson.JsonArray();
        for (var texts : s.alerts()) {
            com.google.gson.JsonObject a = new com.google.gson.JsonObject();
            com.google.gson.JsonObject title = new com.google.gson.JsonObject();
            texts.forEach(title::addProperty);
            a.add("title", title);
            alerts.add(a);
        }
        o.add("alerts", alerts);
        return o.toString();
    }
}
