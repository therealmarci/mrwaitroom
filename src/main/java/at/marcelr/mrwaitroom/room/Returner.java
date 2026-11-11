package at.marcelr.mrwaitroom.room;

import at.marcelr.mrwaitroom.MRwaitroom;
import at.marcelr.mrwaitroom.Settings;
import at.marcelr.mrwaitroom.link.MainLink;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
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
 * with the right "maintenance" of the table waiting-room.rights go back ({@link Rights}). Whoever does not get back
 * after return.max-attempts tries is disconnected: the main server turned them away (ban, whitelist ...). Players
 * with the right "stay" are never sent back by themselves (they go with the back item).
 */
public final class Returner implements Listener {

    private static final String CHANNEL = "BungeeCord";
    /** A try counts as failed if the player is still here this long after it. */
    private static final long TRY_MILLIS = 10_000L;
    /** Nobody is sent back if the main server goes down within this time. */
    private static final long RESTART_SOON = 30_000L;

    /** A player who bounced back within this time keeps counting tries (instead of starting at 0 again). */
    private static final long BOUNCE_MILLIS = 2 * 60_000L;

    /**
     * @param since   first arrival of this stay (longest waiting first)
     * @param arrived last arrival (a player who bounced back arrives again)
     * @param tries   tries to go back during this stay
     */
    private record Waiting(long since, long arrived, int tries, long lastTry) {
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
            long now = System.currentTimeMillis();
            waiting.putIfAbsent(p.getUniqueId(), new Waiting(now, now, 0, 0));
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Whoever comes back shortly after a try (the main server turned them away or sent them here again) keeps their
     * tries - so nobody bounces back and forth forever. The entries stay after leaving for that reason.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        long now = System.currentTimeMillis();
        Waiting old = waiting.get(event.getPlayer().getUniqueId());
        waiting.put(event.getPlayer().getUniqueId(), old != null && now - old.lastTry() < BOUNCE_MILLIS
                ? new Waiting(old.since(), now, old.tries(), old.lastTry())
                : new Waiting(now, now, 0, 0));
    }

    /**
     * The back item and the menu: to the main server right away while it may take the player - without the
     * countdown, during a maintenance only with the right "maintenance".
     */
    public void goBack(Player player) {
        if (mayGo(player, plugin.link().view())) {
            send(player);
        } else {
            plugin.messages().send(player, "main-not-now");
        }
    }

    /** Whether the player may go back right now (main server ready, or in maintenance with the right "maintenance"). */
    public boolean mayGo(Player player, MainLink.View view) {
        // A restart is about to come: stay (otherwise players bounce back and forth right before it).
        var restart = view.status() == null ? null : view.status().restart();
        if (restart != null && restart.at() - System.currentTimeMillis() < RESTART_SOON) {
            return false;
        }
        return switch (view.phase()) {
            case READY -> true;
            // Only while the main server is up - it may restart during the maintenance.
            case MAINTENANCE -> view.readySince() != 0 && plugin.rights().allows(player, Rights.Right.MAINTENANCE);
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
        waiting.entrySet().removeIf(e -> Bukkit.getPlayer(e.getKey()) == null && now - e.getValue().arrived() > 10 * 60_000L
                && now - e.getValue().lastTry() > 10 * 60_000L);
        List<Player> due = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Waiting w = waiting.get(player.getUniqueId());
            // Only with an answer from after the arrival: the main server may just have sent the player here
            // because it goes down - the last answer before does not know that yet.
            if (w == null || view.checkedAt() <= w.arrived() || plugin.rights().allows(player, Rights.Right.STAY)
                    || !mayGo(player, view)
                    || now - w.lastTry() < TRY_MILLIS) {
                continue;
            }
            // During a maintenance one refusal is enough: the main server does not see the bypass the waiting room
            // sees (other permissions or OP list) - stay until the maintenance is over instead of bouncing.
            if (view.phase() == MainLink.Phase.MAINTENANCE && w.tries() > 0) {
                continue;
            }
            if (w.tries() >= s.maxAttempts()) {
                // During a maintenance the main server keeps them out (the bypass seen here may differ, e.g. without
                // a shared LuckPerms): stay and wait for the end. Otherwise it turned them away for good.
                if (view.phase() == MainLink.Phase.READY) {
                    plugin.getLogger().info(player.getName() + " could not get back to the main server " + w.tries()
                            + " times - disconnecting.");
                    player.kick(plugin.messages().as(player, () -> plugin.messages().get("kick-refused")));
                }
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
        long now = System.currentTimeMillis();
        Waiting w = waiting.getOrDefault(player.getUniqueId(), new Waiting(now, now, 0, 0));
        waiting.put(player.getUniqueId(), new Waiting(w.since(), w.arrived(), w.tries() + 1, now));
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
