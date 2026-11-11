package at.marcelr.mrwaitroom.room;

import at.marcelr.mrwaitroom.MRwaitroom;
import at.marcelr.mrwaitroom.Settings;
import at.marcelr.mrwaitroom.link.MainLink;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends the waiting players back to the main server once it is ready (return.delay-seconds after it said so), in
 * batches (return.batch-size every return.batch-seconds), longest waiting first. During a maintenance only players
 * with its bypass permission go back (checked here - with LuckPerms on a shared database the permission is the same
 * as on the main server). Whoever does not get back after return.max-attempts tries is disconnected: the main server
 * turned them away (ban, whitelist ...). Players with mrwaitroom.stay are never sent back by themselves.
 */
public final class Returner implements Listener {

    public static final String PERM_STAY = "mrwaitroom.stay";
    private static final String CHANNEL = "BungeeCord";
    /** A try counts as failed if the player is still here this long after it. */
    private static final long TRY_MILLIS = 10_000L;
    /** Nobody is sent back if the main server goes down within this time. */
    private static final long RESTART_SOON = 30_000L;

    private record Waiting(long since, int tries, long lastTry) {
    }

    private final MRwaitroom plugin;
    private final Map<UUID, Waiting> waiting = new ConcurrentHashMap<>();
    private BukkitTask task;
    private long lastBatch;

    public Returner(MRwaitroom plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!Bukkit.getMessenger().isOutgoingChannelRegistered(plugin, CHANNEL)) {
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            waiting.putIfAbsent(p.getUniqueId(), new Waiting(System.currentTimeMillis(), 0, 0));
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        waiting.put(event.getPlayer().getUniqueId(), new Waiting(System.currentTimeMillis(), 0, 0));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        waiting.remove(event.getPlayer().getUniqueId());
    }

    /** Whether the player may go back right now (main server ready, or in maintenance with the bypass). */
    public boolean mayGo(Player player, MainLink.View view) {
        // A restart is about to come: stay (otherwise players bounce back and forth right before it).
        var restart = view.status() == null ? null : view.status().restart();
        if (restart != null && restart.at() - System.currentTimeMillis() < RESTART_SOON) {
            return false;
        }
        return switch (view.phase()) {
            case READY -> true;
            case MAINTENANCE -> view.status() != null && view.status().maintenance() != null
                    && player.hasPermission(view.status().maintenance().bypass());
            default -> false;
        };
    }

    private void tick() {
        Settings.ReturnSettings s = plugin.settings().back();
        MainLink.View view = plugin.link().view();
        long now = System.currentTimeMillis();
        if (view.readySince() == 0 || now - view.readySince() < s.delaySeconds() * 1000L
                || now - lastBatch < s.batchSeconds() * 1000L) {
            return;
        }
        List<Player> due = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Waiting w = waiting.get(player.getUniqueId());
            // Only with an answer from after the arrival: the main server may just have sent the player here
            // because it goes down - the last answer before does not know that yet.
            if (w == null || view.checkedAt() <= w.since() || player.hasPermission(PERM_STAY) || !mayGo(player, view)
                    || now - w.lastTry() < TRY_MILLIS) {
                continue;
            }
            if (w.tries() >= s.maxAttempts()) {
                plugin.getLogger().info(player.getName() + " could not get back to the main server " + w.tries()
                        + " times - disconnecting.");
                player.kick(plugin.messages().as(player, () -> plugin.messages().get("kick-refused")));
                continue;
            }
            due.add(player);
        }
        if (due.isEmpty()) {
            return;
        }
        due.sort(Comparator.comparingLong(p -> waiting.get(p.getUniqueId()).since()));
        lastBatch = now;
        for (Player player : due.subList(0, Math.min(s.batchSize(), due.size()))) {
            send(player);
        }
    }

    /** Sends the player to the main server now (also for /main and /mrwaitroom send). */
    public void send(Player player) {
        Waiting w = waiting.getOrDefault(player.getUniqueId(), new Waiting(System.currentTimeMillis(), 0, 0));
        waiting.put(player.getUniqueId(), new Waiting(w.since(), w.tries() + 1, System.currentTimeMillis()));
        plugin.messages().send(player, "returning");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(plugin.settings().main().server());
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Could not send " + player.getName() + " to the main server: " + e.getMessage());
        }
    }
}
