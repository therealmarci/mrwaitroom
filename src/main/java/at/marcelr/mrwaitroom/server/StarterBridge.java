package at.marcelr.mrwaitroom.server;

import at.marcelr.mrwaitroom.MRwaitroom;
import at.marcelr.mrwaitroom.Settings;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Talks to MRadmin's starter (mrserver), which starts this server, brings it back after restarts and crashes and keeps
 * Paper up to date. The starter is part of MRadmin's JAR but needs no MRadmin on this server - only these small files in
 * {@code plugins/MRadmin/starter/} (the same as MRadmin's {@code starter/StarterFiles}):
 * <ul>
 *     <li>{@code heartbeat} - written every second while the server ticks (the starter ends a hanging server),</li>
 *     <li>{@code action} - right before going down: {@code restart} or {@code stop},</li>
 *     <li>{@code launch.properties} - memory, Paper updates, crash rules from the section {@code server},</li>
 *     <li>{@code request} / {@code request.answer} - {@code mrserver restart|stop|cancel} from the console.</li>
 * </ul>
 * Without the starter (environment variable MRADMIN_STARTER missing) nothing happens here.
 */
public final class StarterBridge {

    /** Set by the starter for the server process: the server folder. */
    private static final String ENV = "MRADMIN_STARTER";

    private final MRwaitroom plugin;
    private final Path dir;
    private volatile long lastTick;
    private BukkitTask tickTask;
    private BukkitTask ioTask;
    /** What the next shutdown means (restart / stop), null = server.plain-stop. */
    private volatile String wish;
    /** Answers a console request (server thread); set by {@link Restarts}. */
    private Consumer<String> requests = r -> { };

    public StarterBridge(MRwaitroom plugin) {
        this.plugin = plugin;
        String env = System.getenv(ENV);
        this.dir = env == null || env.isBlank() ? null
                : Path.of(env).toAbsolutePath().normalize().resolve("plugins").resolve("MRadmin").resolve("starter");
    }

    /** Whether the starter runs this server (and so brings it back after a restart). */
    public boolean active() {
        return dir != null;
    }

    public void onRequest(Consumer<String> handler) {
        this.requests = handler;
    }

    public void start() {
        stop();
        if (!active()) {
            return;
        }
        writeLaunch();
        lastTick = System.currentTimeMillis();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> lastTick = System.currentTimeMillis(), 20L, 20L);
        ioTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::io, 20L, 20L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (ioTask != null) {
            ioTask.cancel();
            ioTask = null;
        }
    }

    /** On disable: a plain /stop does what server.plain-stop says, unless a restart or stop was planned. */
    public void shutdown() {
        stop();
        if (active() && !Files.isRegularFile(dir.resolve("action"))) {
            write("action", wish != null ? wish : plugin.settings().server().plainStop());
        }
    }

    /** Right before going down on purpose. */
    public void prepare(boolean restart) {
        wish = restart ? "restart" : "stop";
        if (active()) {
            write("action", wish);
        }
    }

    // ------------------------------------------------------------------ files

    private void io() {
        write("heartbeat", String.valueOf(lastTick));
        Path request = dir.resolve("request");
        if (Files.isRegularFile(request)) {
            try {
                String text = Files.readString(request, StandardCharsets.UTF_8).trim();
                Files.deleteIfExists(request);
                Bukkit.getScheduler().runTask(plugin, () -> requests.accept(text));
            } catch (IOException ignored) {
                // next second
            }
        }
    }

    /** The answer to a console request (mrserver shows it). */
    public void answer(String text) {
        if (active()) {
            write("request.answer", text);
        }
    }

    /** The starter settings from the section server (memory, Paper updates, crash rules). */
    public void writeLaunch() {
        if (!active()) {
            return;
        }
        Settings.ServerSettings s = plugin.settings().server();
        Properties p = new Properties();
        p.setProperty("memory-min", s.memoryMin());
        p.setProperty("memory-max", s.memoryMax());
        p.setProperty("java-flags", s.javaFlags());
        p.setProperty("paper-update", s.paperUpdate());
        p.setProperty("minecraft", Bukkit.getMinecraftVersion());
        p.setProperty("paper-build", String.valueOf(paperBuild()));
        p.setProperty("crash-max", "3");
        p.setProperty("crash-window-minutes", "15");
        p.setProperty("hang-seconds", String.valueOf(s.hangSeconds()));
        p.setProperty("stop-timeout-seconds", "180");
        try {
            StringWriter out = new StringWriter();
            p.store(out, "Written by MRwaitroom from config.yml (section server) - do not edit.");
            Files.createDirectories(dir);
            atomic(dir.resolve("launch.properties"), out.toString());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write the starter settings: " + e.getMessage());
        }
    }

    /** The running Paper build, -1 if unknown ("26.3-243-abc (MC: 26.3)" -> 243). */
    public static int paperBuild() {
        var m = java.util.regex.Pattern.compile("-(\\d+)-").matcher(Bukkit.getVersion());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** Properties the starter wrote (state.properties), empty if none. */
    public Properties state() {
        Properties p = new Properties();
        if (active() && Files.isRegularFile(dir.resolve("state.properties"))) {
            try (Reader in = Files.newBufferedReader(dir.resolve("state.properties"), StandardCharsets.UTF_8)) {
                p.load(in);
            } catch (IOException ignored) {
                // empty
            }
        }
        return p;
    }

    private void write(String name, String text) {
        try {
            Files.createDirectories(dir);
            atomic(dir.resolve(name), text);
        } catch (IOException e) {
            plugin.getLogger().fine("Could not write " + name + " for the starter: " + e.getMessage());
        }
    }

    private static void atomic(Path file, String text) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
