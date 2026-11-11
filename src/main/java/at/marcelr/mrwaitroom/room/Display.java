package at.marcelr.mrwaitroom.room;

import at.marcelr.mrwaitroom.MRwaitroom;
import at.marcelr.mrwaitroom.Messages;
import at.marcelr.mrwaitroom.Settings;
import at.marcelr.mrwaitroom.link.MainLink;
import at.marcelr.mrwaitroom.link.MainStatus;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static at.marcelr.mrwaitroom.Messages.tag;

/**
 * What the waiting players see, renewed every second, each player in their own language:
 * <ul>
 *     <li>an item in the hotbar whose name says how long it takes - a new name every second keeps it visible above
 *     the hotbar,</li>
 *     <li>a bossbar with the reason and the progress (display.bossbar),</li>
 *     <li>on joining one chat line with the details in a hover, plus the warnings that are on.</li>
 * </ul>
 */
public final class Display implements Listener {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd.MM. HH:mm");

    private final MRwaitroom plugin;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private BukkitTask task;

    public Display(MRwaitroom plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Map.Entry<UUID, BossBar> e : bars.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                p.hideBossBar(e.getValue());
            }
        }
        bars.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // After Room.receive (same event, lower priority) - one tick later the client is ready for it.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            show(player, plugin.link().view(), System.currentTimeMillis());
            greet(player);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BossBar bar = bars.remove(event.getPlayer().getUniqueId());
        if (bar != null) {
            event.getPlayer().hideBossBar(bar);
        }
    }

    private void tick() {
        MainLink.View view = plugin.link().view();
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            show(player, view, now);
        }
    }

    /** Item and bossbar for one player. */
    private void show(Player player, MainLink.View view, long now) {
        Messages m = plugin.messages();
        Settings.DisplaySettings s = plugin.settings().display();
        Line line = line(view, now);
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack item = player.getInventory().getItem(s.slot());
            if (item == null || item.getType() != s.item()) {
                item = new ItemStack(s.item());
            }
            ItemMeta meta = item.getItemMeta();
            meta.itemName(m.as(player, () -> m.get(line.item(), line.tags())).decoration(TextDecoration.ITALIC, false));
            meta.lore(m.as(player, () -> m.lore("item-lore", line.tags())));
            item.setItemMeta(meta);
            // The amount shows the minutes left (1-64), as a second hint.
            item.setAmount((int) Math.clamp(line.minutes(), 1, 64));
            player.getInventory().setItem(s.slot(), item);
            if (player.getInventory().getHeldItemSlot() != s.slot()) {
                player.getInventory().setHeldItemSlot(s.slot());
            }
        }
        if (!s.bossbar()) {
            BossBar old = bars.remove(player.getUniqueId());
            if (old != null) {
                player.hideBossBar(old);
            }
            return;
        }
        Component title = m.as(player, () -> m.get(line.bar(), line.tags()));
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(title, line.progress(), s.bossbarColor(), BossBar.Overlay.PROGRESS);
            bars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
        } else {
            bar.name(title);
            bar.progress(line.progress());
            bar.color(s.bossbarColor());
        }
    }

    /** One chat line on joining (details in the hover) and the warnings of the main server. */
    public void greet(Player player) {
        Messages m = plugin.messages();
        MainLink.View view = plugin.link().view();
        Line line = line(view, System.currentTimeMillis());
        m.run(player, () -> {
            m.send(player, line.join(), line.tags());
            MainStatus status = view.status();
            if (status != null) {
                for (Map<String, String> alert : status.alerts()) {
                    m.send(player, "alert", tag("title", MainStatus.in(alert, m.languageOf(player))));
                }
            }
        });
    }

    // ------------------------------------------------------------------- texts

    /**
     * Which texts and values fit the phase.
     *
     * @param minutes minutes left (for the amount of the item), at least 1
     */
    record Line(String item, String bar, String join, float progress, long minutes, TagResolver[] tags) {
    }

    Line line(MainLink.View view, long now) {
        MainStatus status = view.status();
        String reason = status != null && status.restart() != null && !status.restart().reason().isEmpty()
                ? status.restart().reason() : plugin.messages().plain("reason-none");
        String name = status != null && !status.name().isEmpty() ? status.name() : plugin.messages().plain("server-name");
        long since = view.downSince() == 0 ? 0 : Math.max(0, now - view.downSince());
        List<TagResolver> tags = new ArrayList<>(List.of(tag("reason", reason), tag("server", name),
                tag("since", clock(since)), tag("estimate", clock(view.estimate()))));
        return switch (view.phase()) {
            case RESTART -> {
                long left = view.estimate() - since;
                tags.add(tag("time", clock(Math.max(0, left))));
                float progress = (float) Math.clamp((double) since / Math.max(1, view.estimate()), 0, 1);
                yield left > 0
                        ? new Line("item-restart", "bossbar-restart", "join-restart", progress, minutes(left), arr(tags))
                        : new Line("item-restart-late", "bossbar-restart", "join-restart", 1f, 1, arr(tags));
            }
            case STOP -> new Line("item-stop", "bossbar-stop", "join-stop", 1f, 1, arr(tags));
            case CRASH -> new Line("item-crash", "bossbar-crash", "join-crash", 1f, 1, arr(tags));
            case STARTING -> new Line("item-starting", "bossbar-starting", "join-starting", 1f, 1, arr(tags));
            case READY -> new Line("item-ready", "bossbar-ready", "join-ready", 1f, 1, arr(tags));
            case MAINTENANCE -> {
                MainStatus.Maintenance mt = status == null ? null : status.maintenance();
                String title = mt == null || mt.title().isEmpty() ? plugin.messages().plain("maintenance-no-title") : mt.title();
                tags.add(tag("title", title));
                if (mt == null || mt.end() <= 0) {
                    yield new Line("item-maintenance-open", "bossbar-maintenance", "join-maintenance-open", 1f, 1, arr(tags));
                }
                tags.add(tag("end", at(mt.end(), now)));
                tags.add(tag("time", clock(Math.max(0, mt.end() - now))));
                long start = mt.start() > 0 ? mt.start() : now;
                float progress = (float) Math.clamp((double) (now - start) / Math.max(1, mt.end() - start), 0, 1);
                yield new Line("item-maintenance", "bossbar-maintenance", "join-maintenance", progress,
                        minutes(mt.end() - now), arr(tags));
            }
        };
    }

    private static TagResolver[] arr(List<TagResolver> tags) {
        return tags.toArray(TagResolver[]::new);
    }

    private static long minutes(long millis) {
        return Math.max(1, (millis + 59_999) / 60_000);
    }

    /** 0:42, 12:05, 1:02:33. */
    public static String clock(long millis) {
        long s = Math.max(0, millis / 1000);
        long h = s / 3600;
        long min = (s % 3600) / 60;
        long sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, min, sec) : String.format("%d:%02d", min, sec);
    }

    /** "18:00" today, otherwise "12.11. 18:00". */
    private String at(long epochMillis, long now) {
        var zone = plugin.settings().zone();
        ZonedDateTime time = Instant.ofEpochMilli(epochMillis).atZone(zone);
        boolean today = time.toLocalDate().equals(LocalDate.ofInstant(Instant.ofEpochMilli(now), zone));
        return (today ? TIME : DAY_TIME).format(time);
    }
}
