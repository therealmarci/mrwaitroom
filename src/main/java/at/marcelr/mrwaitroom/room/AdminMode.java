package at.marcelr.mrwaitroom.room;

import at.marcelr.mrwaitroom.MRwaitroom;
import at.marcelr.mrwaitroom.Messages;
import at.marcelr.mrwaitroom.link.MainStatus;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The admin mode of MRadmin, here too - one state per player for the whole network. Switched here with the item in
 * the hotbar (right {@code admin-mode} of the table), the main server takes it over when the player gets there; who
 * is in admin mode over there is in admin mode here. In admin mode: creative mode, flying, invisible for the waiting
 * players, and the rights with "admin-mode" apply (by default: stay here, on the main server during maintenance).
 * <p>
 * What is switched here goes to MRadmin right away ({@code POST /waiting-room/admin-mode}) and is repeated until the
 * status answer shows it - while the main server is down it simply counts here.
 */
public final class AdminMode implements Listener {

    static final NamespacedKey ITEM = new NamespacedKey("mrwaitroom", "admin-mode");
    /** Slot of the admin mode item (left; the clock is in the middle, the back item right). */
    static final int SLOT = 0;

    private final MRwaitroom plugin;
    /** Switched here and not shown by MRadmin yet: player -> on. */
    private final Map<UUID, Boolean> wishes = new ConcurrentHashMap<>();
    /** What was applied to the online players (creative mode, visibility). */
    private final Map<UUID, Boolean> applied = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private volatile long lastSend;
    private BukkitTask task;

    public AdminMode(MRwaitroom plugin) {
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
    }

    /** Whether the player is in admin mode here (switched here, or in admin mode on the main server - and may use it). */
    public boolean on(Player player) {
        UUID id = player.getUniqueId();
        Boolean wish = wishes.get(id);
        boolean raw;
        if (wish != null) {
            raw = wish;
        } else {
            MainStatus status = plugin.link().view().status();
            raw = status != null && status.adminMode().contains(id.toString());
        }
        return raw && plugin.rights().mayUseAdminMode(player);
    }

    /** The item: switches the admin mode. */
    public void toggle(Player player) {
        Messages m = plugin.messages();
        if (!plugin.rights().mayUseAdminMode(player)) {
            m.send(player, "admin-mode-denied");
            return;
        }
        boolean now = !on(player);
        wishes.put(player.getUniqueId(), now);
        apply(player);
        plugin.room().refreshVisibility();
        m.send(player, now ? "admin-mode-on" : "admin-mode-off");
        player.playSound(player.getLocation(), now ? org.bukkit.Sound.BLOCK_BEACON_ACTIVATE : org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.2f);
        plugin.getLogger().info(player.getName() + (now ? " switched the admin mode on." : " switched the admin mode off."));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::send);
    }

    /** Game mode and flying for the state. Leaving the admin mode empties the inventory (the hotbar comes back). */
    private void apply(Player player) {
        boolean on = on(player);
        Boolean before = applied.put(player.getUniqueId(), on);
        if (on) {
            player.setGameMode(GameMode.CREATIVE);
            player.setAllowFlight(true);
        } else if (before != null && before) {
            player.getInventory().clear();
            player.setGameMode(GameMode.ADVENTURE);
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    private void tick() {
        MainStatus status = plugin.link().view().status();
        if (status != null) {
            // Shown by MRadmin now: no need to send it again.
            wishes.entrySet().removeIf(e -> status.adminMode().contains(e.getKey().toString()) == e.getValue());
        }
        if (!wishes.isEmpty() && System.currentTimeMillis() - lastSend > 5_000L) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::send);
        }
        boolean changed = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Boolean was = applied.get(player.getUniqueId());
            if (was == null || was != on(player)) {
                apply(player);
                changed = true;
            }
        }
        if (changed) {
            plugin.room().refreshVisibility();
        }
    }

    /** Sends what was switched here to MRadmin (not on the server thread). */
    private void send() {
        var s = plugin.settings().main();
        if (s.linkUrl().isEmpty() || wishes.isEmpty()) {
            return;
        }
        lastSend = System.currentTimeMillis();
        JsonObject players = new JsonObject();
        wishes.forEach((id, on) -> players.addProperty(id.toString(), on));
        JsonObject body = new JsonObject();
        body.add("players", players);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(s.linkUrl() + "/waiting-room/admin-mode"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + s.linkKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                plugin.getLogger().fine("MRadmin did not take the admin mode: " + response.statusCode());
            }
        } catch (IOException e) {
            // the main server is down - sent again with the next try
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ item

    /** The item in the hotbar: shows the state, right-click switches. */
    ItemStack item(Player player) {
        Messages m = plugin.messages();
        boolean on = on(player);
        ItemStack item = new ItemStack(on ? Material.COMMAND_BLOCK : Material.STRUCTURE_VOID);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(m.as(player, () -> m.get(on ? "admin-mode-item-on" : "admin-mode-item-off"))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(m.as(player, () -> m.lore(on ? "admin-mode-item-on-lore" : "admin-mode-item-off-lore")));
        meta.setEnchantmentGlintOverride(on);
        meta.getPersistentDataContainer().set(ITEM, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    static boolean isItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(ITEM);
    }

    // -------------------------------------------------------------- listener

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && isItem(event.getItem())) {
            event.setCancelled(true);
            toggle(event.getPlayer());
        }
    }

    /** After Room.receive (adventure mode): creative mode again for players in admin mode. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        applied.remove(event.getPlayer().getUniqueId());
        apply(event.getPlayer());
        plugin.room().refreshVisibility();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        applied.remove(event.getPlayer().getUniqueId());
    }
}
