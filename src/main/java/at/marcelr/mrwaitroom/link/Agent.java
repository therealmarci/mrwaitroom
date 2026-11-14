package at.marcelr.mrwaitroom.link;

import at.marcelr.mrwaitroom.MRwaitroom;
import at.marcelr.mrwaitroom.Settings;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The way in for MRadmin on the main server (section agent): MRadmin reads the performance of this server through a
 * small HTTP endpoint every second and shows it like the one of the main server - charts, recording, lag warnings.
 * Protected with the shared key (main.link-key); 10 wrong keys a minute from one address and it is refused for a while.
 * <pre>
 *   GET /agent/health?after=&lt;ms&gt;   points since then (one per second, the last 5 minutes are kept) + worlds
 *   GET /agent/system                    uptime, Java, cores, disk, server folder size, worlds, newest point
 *   GET /agent/lag                       the 5 chunks with the most entities (counted at most every 30 s)
 * </pre>
 * A point has exactly the fields of MRadmin's health points (HealthMonitor.Point), so MRadmin keeps them like its own.
 */
public final class Agent {

    /** One measurement - the same fields as MRadmin's HealthMonitor.Point. */
    record Point(long time, double tps, double mspt, double msptP95, double msptMax, double cpu, double cpuSystem,
                 long heapUsed, long heapMax, double gc, double gcMax, int players, int chunks, int entities, int tiles,
                 double ping, int loaderChunks) {
    }

    record WorldInfo(String name, String environment, int chunks, int entities, int tiles, int players) {
    }

    private static final Gson GSON = new Gson();
    private static final long KEEP_MILLIS = 5 * 60_000L;
    private static final int MAX_FAILURES = 10;
    private static final long LAG_CACHE_MILLIS = 30_000L;
    private static final long SIZE_CACHE_MILLIS = 10 * 60_000L;

    private final MRwaitroom plugin;
    private final Deque<Point> points = new ArrayDeque<>();
    private final Map<String, int[]> failures = new ConcurrentHashMap<>();
    private volatile List<WorldInfo> worlds = List.of();
    private volatile List<Map<String, Object>> lag = List.of();
    private volatile long lagTime;
    private volatile long serverSize = -1;
    private volatile long sizeTime;
    private final AtomicBoolean measuring = new AtomicBoolean();
    private HttpServer server;
    private BukkitTask sampler;
    private BukkitTask tickCounter;
    private int ticks;
    private long lastSample;
    private long lastGcTime = -1;
    private long failureMinute;

    public Agent(MRwaitroom plugin) {
        this.plugin = plugin;
    }

    private Settings.AgentSettings settings() {
        return plugin.settings().agent();
    }

    // --------------------------------------------------------------- lifecycle

    /** Opens the port (agent.port, 0 = off) and measures every second. */
    public void start() {
        stop();
        Settings.AgentSettings s = settings();
        if (s.port() <= 0) {
            return;
        }
        if (plugin.settings().main().linkKey().isEmpty()) {
            plugin.getLogger().warning("agent.port is set but main.link-key is empty - MRadmin cannot read this server.");
            return;
        }
        try {
            InetSocketAddress address = s.bind().isEmpty() ? new InetSocketAddress(s.port()) : new InetSocketAddress(s.bind(), s.port());
            HttpServer created = HttpServer.create(address, 16);
            created.createContext("/agent/", this::handle);
            created.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            created.start();
            server = created;
            plugin.getLogger().info("Agent for MRadmin listening on " + (s.bind().isEmpty() ? "*" : s.bind()) + ":" + s.port() + ".");
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Could not open the agent port " + s.port() + ": " + e.getMessage());
            return;
        }
        lastSample = System.currentTimeMillis();
        ticks = 0;
        tickCounter = Bukkit.getScheduler().runTaskTimer(plugin, () -> ticks++, 1L, 1L);
        sampler = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, 20L, 20L);
    }

    public void stop() {
        if (sampler != null) {
            sampler.cancel();
            sampler = null;
        }
        if (tickCounter != null) {
            tickCounter.cancel();
            tickCounter = null;
        }
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /** The port MRadmin should ask (0 = the agent is not running) - reported with every report. */
    public int port() {
        return server == null ? 0 : settings().port();
    }

    // ----------------------------------------------------------------- sample

    /** Server thread, every 20 ticks: one point, measured like MRadmin measures its own server. */
    private void sample() {
        long now = System.currentTimeMillis();
        double elapsed = Math.max(0.001, (now - lastSample) / 1000.0);
        double tps = Math.min(20, ticks / elapsed);
        ticks = 0;
        lastSample = now;

        long[] times = Bukkit.getTickTimes().clone();
        Arrays.sort(times);
        double p95 = times.length == 0 ? 0 : times[(int) Math.min(times.length - 1, Math.ceil(times.length * 0.95) - 1)] / 1e6;
        double max = times.length == 0 ? 0 : times[times.length - 1] / 1e6;

        double cpu = -1;
        double cpuSystem = -1;
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            cpu = percent(os.getProcessCpuLoad());
            cpuSystem = percent(os.getCpuLoad());
        }
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcTime += Math.max(0, gc.getCollectionTime());
        }
        double gc = lastGcTime < 0 ? 0 : Math.max(0, gcTime - lastGcTime) / elapsed;
        lastGcTime = gcTime;

        int chunks = 0;
        int entities = 0;
        int tiles = 0;
        List<WorldInfo> list = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            int c = world.getChunkCount();
            int e = world.getEntityCount();
            int t = world.getTileEntityCount();
            chunks += c;
            entities += e;
            tiles += t;
            list.add(new WorldInfo(world.getName(), world.getEnvironment().name().toLowerCase(Locale.ROOT), c, e, t,
                    world.getPlayerCount()));
        }
        worlds = List.copyOf(list);
        double ping = Bukkit.getOnlinePlayers().stream().mapToInt(Player::getPing).average().orElse(-1);

        Point point = new Point(now, round(tps), round(Bukkit.getAverageTickTime()), round(p95), round(max), cpu, cpuSystem,
                heap.getUsed() / 1_048_576, (heap.getMax() > 0 ? heap.getMax() : heap.getCommitted()) / 1_048_576,
                round(gc), round(gc), Bukkit.getOnlinePlayers().size(), chunks, entities, tiles,
                ping < 0 ? -1 : round(ping), -1);
        synchronized (points) {
            points.addLast(point);
            while (!points.isEmpty() && points.peekFirst().time() < now - KEEP_MILLIS) {
                points.removeFirst();
            }
        }
    }

    // ------------------------------------------------------------------ http

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
            if (blocked(ip)) {
                send(exchange, 429, Map.of("error", "too-many-attempts"));
                return;
            }
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            String key = plugin.settings().main().linkKey();
            if (key.isEmpty() || auth == null || !MessageDigest.isEqual(auth.getBytes(StandardCharsets.UTF_8),
                    ("Bearer " + key).getBytes(StandardCharsets.UTF_8))) {
                failed(ip);
                send(exchange, 401, Map.of("error", "wrong-key"));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            switch (method + " " + path) {
                case "GET /agent/health" -> send(exchange, 200, health(number(query.get("after"))));
                case "GET /agent/system" -> send(exchange, 200, system());
                case "GET /agent/lag" -> send(exchange, 200, lag());
                default -> send(exchange, 404, Map.of("error", "not-found"));
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Agent request failed: " + e);
        }
    }

    private Map<String, Object> health(long after) {
        List<Point> list;
        synchronized (points) {
            list = points.stream().filter(p -> p.time() > after).toList();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("points", list);
        // The 1-minute average decides the lag warning (as on the main server - one spike does not count).
        result.put("tps1m", Math.min(20, round(Bukkit.getTPS()[0])));
        result.put("worlds", worlds);
        return result;
    }

    private Map<String, Object> system() {
        Map<String, Object> info = new LinkedHashMap<>();
        var runtime = ManagementFactory.getRuntimeMXBean();
        info.put("uptime", runtime.getUptime());
        info.put("java", System.getProperty("java.vendor") + " " + System.getProperty("java.version"));
        info.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        info.put("cores", Runtime.getRuntime().availableProcessors());
        info.put("threads", ManagementFactory.getThreadMXBean().getThreadCount());
        info.put("server", Bukkit.getName() + " " + Bukkit.getMinecraftVersion());
        info.put("plugins", Bukkit.getPluginManager().getPlugins().length);
        File root = new File(".").getAbsoluteFile();
        info.put("diskFree", root.getUsableSpace() / 1_048_576);
        info.put("diskTotal", root.getTotalSpace() / 1_048_576);
        measureSize(root);
        info.put("serverSize", serverSize < 0 ? -1 : serverSize / 1_048_576.0);
        info.put("dbSize", -1);
        info.put("storage", "-");
        info.put("worlds", worlds);
        synchronized (points) {
            info.put("latest", points.peekLast());
        }
        return info;
    }

    private void measureSize(File root) {
        if (System.currentTimeMillis() - sizeTime < SIZE_CACHE_MILLIS || !measuring.compareAndSet(false, true)) {
            return;
        }
        Thread.ofVirtual().name("MRwaitroom-Size").start(() -> {
            try {
                long[] total = {0};
                java.nio.file.Files.walkFileTree(root.toPath(), new java.nio.file.SimpleFileVisitor<>() {
                    @Override
                    public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file,
                            java.nio.file.attribute.BasicFileAttributes attrs) {
                        total[0] += attrs.size();
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }

                    @Override
                    public java.nio.file.FileVisitResult visitFileFailed(java.nio.file.Path file, IOException e) {
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                });
                serverSize = total[0];
                sizeTime = System.currentTimeMillis();
            } catch (IOException e) {
                serverSize = -1;
            } finally {
                measuring.set(false);
            }
        });
    }

    /** The chunks with the most entities - counting walks every entity, so at most every 30 seconds. */
    private Map<String, Object> lag() {
        if (System.currentTimeMillis() - lagTime > LAG_CACHE_MILLIS) {
            try {
                lag = Bukkit.getScheduler().callSyncMethod(plugin, this::countLag).get(5, TimeUnit.SECONDS);
                lagTime = System.currentTimeMillis();
            } catch (Exception e) {
                // server busy or stopping - the last count stays
            }
        }
        return Map.of("time", lagTime, "chunks", lag);
    }

    private List<Map<String, Object>> countLag() {
        record Key(World world, int x, int z) {
        }
        Map<Key, Map<EntityType, Integer>> counts = new HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player) {
                    continue;
                }
                Chunk c = entity.getChunk();
                counts.computeIfAbsent(new Key(world, c.getX(), c.getZ()), k -> new HashMap<>())
                        .merge(entity.getType(), 1, Integer::sum);
            }
        }
        List<Map<String, Object>> list = new ArrayList<>();
        counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(sum(b.getValue()), sum(a.getValue())))
                .limit(5)
                .forEach(e -> {
                    List<Map<String, Object>> types = new ArrayList<>();
                    e.getValue().entrySet().stream().sorted(Map.Entry.<EntityType, Integer>comparingByValue().reversed())
                            .limit(4).forEach(t -> types.add(Map.of("type", t.getKey().name().toLowerCase(Locale.ROOT), "count", t.getValue())));
                    list.add(Map.of("world", e.getKey().world().getName(), "x", e.getKey().x() * 16 + 8,
                            "z", e.getKey().z() * 16 + 8, "count", sum(e.getValue()), "types", types));
                });
        return list;
    }

    private static int sum(Map<EntityType, Integer> m) {
        return m.values().stream().mapToInt(Integer::intValue).sum();
    }

    // ----------------------------------------------------------------- helpers

    private boolean blocked(String ip) {
        long minute = System.currentTimeMillis() / 60_000L;
        if (minute != failureMinute) {
            failureMinute = minute;
            failures.clear();
        }
        int[] n = failures.get(ip);
        return n != null && n[0] >= MAX_FAILURES;
    }

    private void failed(String ip) {
        failures.computeIfAbsent(ip, k -> new int[1])[0]++;
    }

    private static void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                map.put(java.net.URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private static long number(String value) {
        try {
            return value == null ? 0 : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double percent(double load) {
        return load < 0 ? -1 : round(load * 100);
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
