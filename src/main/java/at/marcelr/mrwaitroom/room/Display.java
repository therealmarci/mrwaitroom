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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static at.marcelr.mrwaitroom.Messages.tag;

/**
 * What the waiting players see, renewed every second, each player in their own language:
 * <ul>
 *     <li>an item in the hotbar (selected) whose name says what is going on - it only changes when the situation
 *     changes: every change makes the game play the "take into hand" animation,</li>
 *     <li>a bossbar with the countdown, the reason and the progress, every second (display.bossbar),</li>
 *     <li>right-clicking the clock opens {@link InfoMenu} with all details,</li>
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
        countdown(view, now);
        for (Player player : Bukkit.getOnlinePlayers()) {
            show(player, view, now);
        }
        InfoMenu.refreshOpen();
    }

    /** Item and bossbar for one player. */
    private void show(Player player, MainLink.View view, long now) {
        Messages m = plugin.messages();
        Settings.DisplaySettings s = plugin.settings().display();
        Line line = line(view, now);
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack item = new ItemStack(s.item());
            ItemMeta meta = item.getItemMeta();
            meta.itemName(m.as(player, () -> m.get(line.item(), line.tags())).decoration(TextDecoration.ITALIC, false));
            meta.lore(m.as(player, () -> m.lore("item-lore", line.tags())));
            item.setItemMeta(meta);
            // Only when it differs: setting the same item again would replay the "take into hand" animation.
            ItemStack current = player.getInventory().getItem(s.slot());
            if (current == null || !current.isSimilar(item) || current.getAmount() != 1) {
                player.getInventory().setItem(s.slot(), item);
            }
            boolean visit = player.hasPermission(Returner.PERM_VISIT);
            if (visit) {
                ItemStack back = backItem(player);
                ItemStack there = player.getInventory().getItem(BACK_SLOT);
                if (there == null || !there.isSimilar(back)) {
                    player.getInventory().setItem(BACK_SLOT, back);
                }
            }
            // The clock stays selected - except for players with the back item (they need to reach it).
            if (!visit && player.getInventory().getHeldItemSlot() != s.slot()) {
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

    // --------------------------------------------------------------- countdown

    /** readySince of the countdown that was announced, so it is announced once. */
    private long announced;

    /** The main server is ready again: one chat line when the countdown starts, a tick in the last 5 seconds. */
    private void countdown(MainLink.View view, long now) {
        if (view.phase() != MainLink.Phase.READY || view.readySince() == 0) {
            return;
        }
        long left = view.readySince() + plugin.settings().back().delaySeconds() * 1000L - now;
        if (announced != view.readySince()) {
            announced = view.readySince();
            if (left > 1000) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!plugin.returner().visitor(p)) {
                        plugin.messages().send(p, "ready-countdown", tag("time", String.valueOf((left + 999) / 1000)));
                    }
                }
            }
        }
        if (left > 0 && left <= 5000) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!plugin.returner().visitor(p)) {
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.4f);
                }
            }
        }
    }

    /** Slot of the back item (players with mrwaitroom.visit). */
    static final int BACK_SLOT = 8;
    static final org.bukkit.NamespacedKey BACK = new org.bukkit.NamespacedKey("mrwaitroom", "back");

    private ItemStack backItem(Player player) {
        Messages m = plugin.messages();
        ItemStack item = new ItemStack(org.bukkit.Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(m.as(player, () -> m.get("back-item-name")).decoration(TextDecoration.ITALIC, false));
        meta.lore(m.as(player, () -> m.lore("back-item-lore")));
        meta.getPersistentDataContainer().set(BACK, org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    /** Whether the item is the back item. */
    public static boolean isBackItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(BACK);
    }

    // ------------------------------------------------------------------- texts

    /**
     * Which texts and values fit the phase.
     *
     * @param key  state: item-&lt;key&gt; names the clock (must not change every second, see the class comment),
     *             bossbar-&lt;key&gt; is the live line with the countdown, menu-state-&lt;key&gt; the text in the menu
     * @param tags all values of the texts (reason, since, time, estimate, title, start, end, server)
     */
    public record Line(String key, String join, float progress, TagResolver[] tags) {
        public String item() {
            return "item-" + key;
        }

        public String bar() {
            return "bossbar-" + key;
        }
    }

    public Line line(MainLink.View view, long now) {
        MainStatus status = view.status();
        Messages m = plugin.messages();
        long since = view.downSince() == 0 ? 0 : Math.max(0, now - view.downSince());
        // All values, so every text (bossbar, menu, chat) may use any of them; what does not apply is "-".
        Map<String, String> v = new LinkedHashMap<>();
        v.put("reason", status != null && status.restart() != null && !status.restart().reason().isEmpty()
                ? status.restart().reason() : m.plain("reason-none"));
        v.put("server", status != null && !status.name().isEmpty() ? status.name() : m.plain("server-name"));
        v.put("since", view.downSince() == 0 ? "-" : clock(since));
        v.put("estimate", clock(view.estimate()));
        v.put("time", "-");
        MainStatus.Maintenance mt = status == null ? null : status.maintenance();
        v.put("title", mt == null || mt.title().isEmpty() ? m.plain("maintenance-no-title") : mt.title());
        v.put("start", mt != null && mt.start() > 0 ? at(mt.start(), now) : "-");
        v.put("end", mt != null && mt.end() > 0 ? at(mt.end(), now) : "-");
        return switch (view.phase()) {
            case RESTART -> {
                long left = view.estimate() - since;
                v.put("time", clock(Math.max(0, left)));
                float progress = (float) Math.clamp((double) since / Math.max(1, view.estimate()), 0, 1);
                yield left > 0
                        ? new Line("restart", "join-restart", progress, tags(v))
                        : new Line("restart-late", "join-restart", 1f, tags(v));
            }
            case STOP -> new Line("stop", "join-stop", 1f, tags(v));
            case CRASH -> new Line("crash", "join-crash", 1f, tags(v));
            case STARTING -> new Line("starting", "join-starting", 1f, tags(v));
            case READY -> {
                // Countdown before going back (return.delay-seconds).
                long delay = plugin.settings().back().delaySeconds() * 1000L;
                long left = view.readySince() == 0 ? 0 : view.readySince() + delay - now;
                v.put("time", String.valueOf(Math.max(0, (left + 999) / 1000)));
                yield left > 0
                        ? new Line("ready", "join-ready", (float) Math.clamp(1 - (double) left / Math.max(1, delay), 0, 1), tags(v))
                        : new Line("returning", "join-ready", 1f, tags(v));
            }
            case MAINTENANCE -> {
                if (mt == null || mt.end() <= 0) {
                    yield new Line("maintenance-open", "join-maintenance-open", 1f, tags(v));
                }
                v.put("time", clock(Math.max(0, mt.end() - now)));
                long start = mt.start() > 0 ? mt.start() : now;
                float progress = (float) Math.clamp((double) (now - start) / Math.max(1, mt.end() - start), 0, 1);
                yield new Line("maintenance", "join-maintenance", progress, tags(v));
            }
        };
    }

    private static TagResolver[] tags(Map<String, String> values) {
        return values.entrySet().stream().map(e -> tag(e.getKey(), e.getValue())).toArray(TagResolver[]::new);
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
