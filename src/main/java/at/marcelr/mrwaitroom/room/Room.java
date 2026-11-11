package at.marcelr.mrwaitroom.room;

import at.marcelr.mrwaitroom.MRwaitroom;
import at.marcelr.mrwaitroom.Settings;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.generator.ChunkGenerator;

/**
 * The room: an empty world (created if missing) with a small platform, everyone in adventure mode without damage or
 * hunger, the inventory locked (only the display item), chat only with mrwaitroom.chat (or room.chat). Falling into
 * the void brings a player back to the spawn.
 */
public final class Room implements Listener {

    public static final String PERM_CHAT = "mrwaitroom.chat";

    private final MRwaitroom plugin;
    private World world;

    public Room(MRwaitroom plugin) {
        this.plugin = plugin;
    }

    /** Loads or creates the world of the waiting room. */
    public void load() {
        Settings.RoomSettings s = plugin.settings().room();
        World existing = Bukkit.getWorld(s.world());
        world = existing != null ? existing : new WorldCreator(s.world()).generator(new Void()).generateStructures(false)
                .createWorld();
        if (world == null) {
            world = Bukkit.getWorlds().getFirst();
            plugin.getLogger().warning("Could not load the world '" + s.world() + "', using " + world.getName() + ".");
            return;
        }
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setTime(6000);
        world.setStorm(false);
        // Set up once: a marker in the world itself (the folder check does not work - Minecraft 26 keeps extra
        // worlds inside the main world).
        org.bukkit.NamespacedKey marker = new org.bukkit.NamespacedKey(plugin, "set-up");
        if (!world.getPersistentDataContainer().has(marker)) {
            world.getPersistentDataContainer().set(marker, org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
            world.setSpawnLocation(0, 65, 0);
            if (s.lobby() && plugin.lobby().place(world)) {
                plugin.getLogger().info("Created the waiting room world  + s.world() +  with the lobby.");
                return;
            }
            if (s.platform()) {
                for (int x = -4; x <= 4; x++) {
                    for (int z = -4; z <= 4; z++) {
                        world.getBlockAt(x, 64, z).setType(s.platformBlock(), false);
                    }
                }
            }
            plugin.getLogger().info("Created the waiting room world '" + s.world() + "'.");
        }
    }

    public World world() {
        return world;
    }

    public Location spawn() {
        Location l = world.getSpawnLocation().toCenterLocation();
        l.setY(world.getSpawnLocation().getY());
        return l;
    }

    /** Puts the player into the room: spawn, adventure mode, empty inventory (the display item comes next). */
    public void receive(Player player) {
        if (!player.getWorld().equals(world)) {
            player.teleport(spawn());
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
        player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null ? 20
                : player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
        if (plugin.settings().room().hidePlayers()) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other != player) {
                    player.hidePlayer(plugin, other);
                    other.hidePlayer(plugin, player);
                }
            }
        }
    }

    // ------------------------------------------------------------- protection

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        receive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                player.teleport(spawn());
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.deathMessage(null);
        event.getDrops().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo().getWorld().equals(world) && event.getTo().getY() < spawn().getY() - 40) {
            event.getPlayer().teleport(spawn());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    /** The display item stays selected, so its name stays visible above the hotbar. */
    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE && !event.getPlayer().hasPermission(Returner.PERM_VISIT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.settings().room().chat() && !event.getPlayer().hasPermission(PERM_CHAT)) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "chat-off");
        }
    }

    /** No terrain at all - only the platform. */
    public static final class Void extends ChunkGenerator {
        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }
    }
}
