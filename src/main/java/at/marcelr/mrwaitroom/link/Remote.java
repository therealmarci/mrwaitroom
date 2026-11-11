package at.marcelr.mrwaitroom.link;

import at.marcelr.mrwaitroom.ConfigUpdater;
import at.marcelr.mrwaitroom.MRwaitroom;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Management from MRadmin (its page Plugins on the main server) - the waiting room asks, MRadmin never connects here:
 * <ul>
 *     <li><b>Report</b> every 30 seconds ({@code POST /waiting-room/report}): version, the config form
 *     (web-config.yml), the config.yml as it is, the bundled defaults, a downloaded update and which orders were
 *     carried out.</li>
 *     <li><b>Orders</b> come with the status answer (every few seconds, {@link MainLink}):
 *     {@code config} = take this config.yml (backup to config.backup-remote.yml, then reload);
 *     {@code update} = download this release - only from this plugin's GitHub releases, only with the right SHA-256
 *     and only if it is MRwaitroom - into the update folder. Afterwards the server restarts as soon as nobody waits and
 *     the main server is ready ({@code updates.restart-when-idle}; a service manager such as systemd starts it again).</li>
 * </ul>
 */
public final class Remote {

    private static final long MAX_JAR = 50L * 1024 * 1024;

    private final MRwaitroom plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Properties info = new Properties();
    /** Orders already taken (also while they are still running), so a repeated order is not carried out twice. */
    private final Set<String> seen = new LinkedHashSet<>();
    private final List<String> done = new ArrayList<>();
    private final List<Map<String, String>> failed = new ArrayList<>();
    private volatile String staged;
    /** Set right before this server restarts - MRadmin then holds back restarts of the main server. */
    private volatile boolean restarting;
    private BukkitTask reportTask;
    private BukkitTask idleTask;

    public Remote(MRwaitroom plugin) {
        this.plugin = plugin;
        try (InputStream in = plugin.getResource("build-info.properties")) {
            if (in != null) {
                info.load(in);
            }
        } catch (IOException ignored) {
            // without it the report has no version - MRadmin then shows nothing
        }
    }

    public void start() {
        stop();
        if (plugin.settings().main().linkUrl().isEmpty()) {
            return;
        }
        reportTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::report, 40L, 30 * 20L);
        idleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::restartWhenIdle, 200L, 200L);
    }

    public void stop() {
        if (reportTask != null) {
            reportTask.cancel();
            reportTask = null;
        }
        if (idleTask != null) {
            idleTask.cancel();
            idleTask = null;
        }
    }

    /** Version waiting in the update folder, or null. */
    public String staged() {
        return staged;
    }

    // ------------------------------------------------------------------- report

    /** Sends the report (any thread except the server thread - it blocks shortly). */
    public void report() {
        var s = plugin.settings().main();
        if (s.linkUrl().isEmpty()) {
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("name", plugin.getName());
        o.addProperty("version", info.getProperty("pluginVersion", ""));
        o.addProperty("commit", plain(info.getProperty("gitCommit")));
        o.addProperty("branch", plain(info.getProperty("gitBranch")));
        o.addProperty("repository", info.getProperty("githubRepository", ""));
        o.addProperty("server", Bukkit.getIp().isEmpty() ? "port " + Bukkit.getPort() : Bukkit.getIp() + ":" + Bukkit.getPort());
        o.addProperty("form", resource("web-config.yml"));
        o.addProperty("defaults", resource("config.yml"));
        try {
            o.addProperty("config", Files.readString(new File(plugin.getDataFolder(), "config.yml").toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            o.addProperty("config", "");
        }
        if (staged != null) {
            o.addProperty("staged", staged);
        }
        // The server itself, for the tile "waiting room server" in MRadmin.
        JsonObject host = new JsonObject();
        host.addProperty("starter", plugin.starter().active());
        host.addProperty("paper", at.marcelr.mrwaitroom.server.StarterBridge.paperBuild());
        host.addProperty("minecraft", Bukkit.getMinecraftVersion());
        host.addProperty("startedAt", java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        host.addProperty("players", Bukkit.getOnlinePlayers().size());
        host.addProperty("nextRestart", plugin.restarts().nextPlanned());
        host.addProperty("pending", plugin.restarts().pending());
        host.addProperty("blocked", plugin.restarts().blocked());
        host.addProperty("paperUpdate", plugin.settings().server().paperUpdate());
        host.addProperty("restarting", restarting);
        o.add("host", host);
        List<String> sentDone;
        List<Map<String, String>> sentFailed;
        synchronized (this) {
            sentDone = List.copyOf(done);
            sentFailed = List.copyOf(failed);
        }
        JsonArray d = new JsonArray();
        sentDone.forEach(d::add);
        o.add("done", d);
        JsonArray f = new JsonArray();
        for (Map<String, String> fail : sentFailed) {
            JsonObject j = new JsonObject();
            fail.forEach(j::addProperty);
            f.add(j);
        }
        o.add("failed", f);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(s.linkUrl() + "/waiting-room/report"))
                    .timeout(Duration.ofSeconds(restarting ? 3 : 10))
                    .header("Authorization", "Bearer " + s.linkKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(o.toString())).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                synchronized (this) {
                    done.removeAll(sentDone);
                    failed.removeAll(sentFailed);
                }
            } else if (response.statusCode() != 401 && response.statusCode() != 429) {
                plugin.getLogger().fine("Report to MRadmin refused: " + response.body());
            }
        } catch (IOException e) {
            // the main server is down - the next report follows
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------- orders

    /**
     * Right before this server restarts: tells MRadmin (blocking, at most a few seconds), so it does not restart or
     * stop the main server while the waiting room is away.
     */
    public void announceRestart() {
        restarting = true;
        report();
    }

    /** Orders from the status answer (link thread). */
    public void take(JsonElement orders) {
        if (!(orders instanceof JsonArray list)) {
            return;
        }
        for (JsonElement e : list) {
            if (!(e instanceof JsonObject order) || !order.has("id") || !plugin.getName().equals(str(order, "plugin"))) {
                continue;
            }
            String id = str(order, "id");
            synchronized (this) {
                if (!seen.add(id)) {
                    continue;
                }
            }
            switch (str(order, "type")) {
                case "config" -> Bukkit.getScheduler().runTask(plugin, () -> applyConfig(id, str(order, "text")));
                case "restart" -> Bukkit.getScheduler().runTask(plugin, () -> {
                    String reason = plugin.restarts().requestByHand(str(order, "by").isEmpty() ? "MRadmin" : str(order, "by"));
                    if (reason == null) {
                        ok(id);
                    } else {
                        fail(id, reason);
                    }
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, this::report);
                });
                case "visit" -> Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        plugin.returner().announceVisit(java.util.UUID.fromString(str(order, "player")));
                        ok(id);
                    } catch (IllegalArgumentException bad) {
                        fail(id, "no player");
                    }
                });
                case "update" -> Bukkit.getScheduler().runTaskAsynchronously(plugin,
                        () -> update(id, str(order, "version"), str(order, "url"), str(order, "sha256")));
                default -> fail(id, "unknown order");
            }
        }
    }

    /** Server thread: take the config.yml from MRadmin's editor and reload. */
    private void applyConfig(String id, String text) {
        try {
            new YamlConfiguration().loadFromString(ConfigUpdater.stripBom(text));
            Path path = new File(plugin.getDataFolder(), "config.yml").toPath();
            if (Files.isRegularFile(path)) {
                Files.copy(path, path.resolveSibling("config.backup-remote.yml"), StandardCopyOption.REPLACE_EXISTING);
            }
            Path temp = path.resolveSibling("config.yml.remote-tmp");
            Files.writeString(temp, text.startsWith(ConfigUpdater.BOM) ? text : ConfigUpdater.BOM + text, StandardCharsets.UTF_8);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            plugin.reload();
            plugin.getLogger().info("Took the configuration from MRadmin and reloaded.");
            ok(id);
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            plugin.getLogger().warning("Could not take the configuration from MRadmin: " + e.getMessage());
            fail(id, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::report);
    }

    /** Background thread: download, check and put an official release into the update folder. */
    private void update(String id, String version, String url, String sha256) {
        try {
            String repository = info.getProperty("githubRepository", "");
            if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
                    || !url.startsWith("https://github.com/" + repository + "/releases/download/")) {
                throw new IOException("not an official release address: " + url);
            }
            if (!sha256.matches("[0-9a-fA-F]{64}")) {
                throw new IOException("no checksum");
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2))
                    .header("User-Agent", "MRwaitroom").GET().build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            byte[] jar = response.body();
            if (jar.length > MAX_JAR) {
                throw new IOException("file too large");
            }
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar));
            if (!actual.equalsIgnoreCase(sha256)) {
                throw new IOException("checksum does not match");
            }
            if (!isThisPlugin(jar)) {
                throw new IOException("the file is not " + plugin.getName());
            }
            File folder = Bukkit.getUpdateFolderFile();
            Files.createDirectories(folder.toPath());
            Path target = new File(folder, plugin.jarFile().getName()).toPath();
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temp, jar);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            staged = version;
            plugin.getLogger().info("Downloaded " + plugin.getName() + " " + version + " (checksum ok) - it is put in on the"
                    + " next start.");
            ok(id);
        } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
            plugin.getLogger().warning("Update from MRadmin failed: " + e.getMessage());
            fail(id, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        report();
    }

    /** The JAR's plugin.yml names this plugin. */
    private boolean isThisPlugin(byte[] jar) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (entry.getName().equals("plugin.yml")) {
                    String yml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    return yml.lines().anyMatch(l -> l.trim().equals("name: " + plugin.getName()));
                }
            }
        }
        return false;
    }

    /**
     * Server thread, every 10 seconds: a downloaded update is put in by restarting - only while nobody waits and the
     * main server is ready (then the waiting room is not needed).
     */
    private void restartWhenIdle() {
        if (staged == null || !plugin.settings().update().restartWhenIdle() || !Bukkit.getOnlinePlayers().isEmpty()
                || plugin.link().view().phase() != MainLink.Phase.READY) {
            return;
        }
        plugin.getLogger().info("Nobody waits and the main server is ready - restarting to put in " + plugin.getName()
                + " " + staged + ".");
        Bukkit.shutdown();
    }

    private synchronized void ok(String id) {
        done.add(id);
    }

    private synchronized void fail(String id, String error) {
        failed.add(Map.of("id", id, "error", error.length() > 200 ? error.substring(0, 200) : error));
    }

    private String resource(String name) {
        try (InputStream in = plugin.getResource(name)) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String plain(String value) {
        return value == null || value.contains("$") ? "" : value.trim();
    }

    private static String str(JsonObject o, String key) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
        } catch (RuntimeException e) {
            return "";
        }
    }
}
