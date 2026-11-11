package at.marcelr.mrwaitroom.room;

import at.marcelr.mrwaitroom.MRwaitroom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The board on a wall of 4 x 2 item frames: every frame gets a map that shows its piece of {@link Board}, each
 * player in their own language. Set up with /mrwaitroom screen set (looking at one of the frames), the frames are
 * then locked. Stored in screen.yml (frames and map numbers) - the maps get their renderer back after a restart.
 * <p>
 * Live: 4 times a second the board is drawn again where something changed, and the changed maps are sent right away
 * to the players near it (Minecraft itself only sends maps in frames now and then).
 */
public final class Screen implements Listener {

    public static final int COLUMNS = 4;
    public static final int ROWS = 2;

    /** Result of setting up. */
    public enum Result { OK, NO_FRAME, NOT_ON_WALL, WRONG_SIZE }

    private final MRwaitroom plugin;
    private final Board board;
    private final File file;
    private final NamespacedKey marker;
    /** Map number -> which piece of the board (0 = top left, row by row). */
    private final Map<Integer, Integer> maps = new HashMap<>();
    /** Map number -> the map. */
    private final Map<Integer, MapView> views = new HashMap<>();
    /** Where the board hangs, null if there is none. */
    private Placement placement;
    /** Per player: fingerprint of every map as last sent. */
    private final Map<UUID, int[]> sent = new ConcurrentHashMap<>();
    private BukkitTask task;

    /** Players this close to the board (blocks) get it live. */
    private static final double RANGE = 64;
    /** Size found when the wall did not fit (for the message). */
    private int[] lastSize = {0, 0};

    public Screen(MRwaitroom plugin) {
        this.plugin = plugin;
        this.board = new Board(plugin);
        this.file = new File(plugin.getDataFolder(), "screen.yml");
        this.marker = new NamespacedKey(plugin, "screen");
    }

    public Board board() {
        return board;
    }

    public boolean active() {
        return !maps.isEmpty();
    }

    public int[] lastSize() {
        return lastSize;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 4L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        sent.clear();
    }

    /** Draws what changed and sends the changed maps to everyone near the board. */
    private void tick() {
        Placement p = placement;
        if (p == null || views.isEmpty()) {
            return;
        }
        World world = Bukkit.getWorld(p.world());
        if (world == null) {
            return;
        }
        Location at = new Location(world, p.x() + 0.5, p.y(), p.z() + 0.5);
        long now = System.currentTimeMillis();
        var view = plugin.link().view();
        Map<String, Board.Picture> pictures = new HashMap<>();
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(at) > RANGE * RANGE) {
                continue;
            }
            Board.Picture picture = pictures.computeIfAbsent(plugin.messages().languageOf(player),
                    language -> board.refresh(language, view, now));
            int[] last = sent.get(player.getUniqueId());
            for (Map.Entry<Integer, Integer> e : maps.entrySet()) {
                int piece = e.getValue();
                if (last == null || last[piece] != picture.tiles()[piece]) {
                    MapView map = views.get(e.getKey());
                    if (map != null) {
                        player.sendMap(map);
                    }
                }
            }
            sent.put(player.getUniqueId(), picture.tiles().clone());
        }
    }

    /** Gives the stored maps their renderer back (after a server start). */
    public void load() {
        maps.clear();
        views.clear();
        placement = null;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        try {
            placement = new Placement(yml.getString("world", ""), yml.getInt("x"), yml.getInt("y"), yml.getInt("z"),
                    BlockFace.valueOf(yml.getString("facing", "NORTH")));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("screen.yml is broken - set the board up again with /mrwaitroom screen set.");
            return;
        }
        List<Integer> ids = yml.getIntegerList("maps");
        for (int i = 0; i < ids.size(); i++) {
            MapView view = Bukkit.getMap(ids.get(i));
            if (view == null) {
                plugin.getLogger().warning("Map #" + ids.get(i) + " of the board is gone - set it up again with /mrwaitroom screen set.");
                continue;
            }
            attach(view, i);
        }
    }

    /** Frame where the board starts (top left) and which way the wall faces - for the lobby template. */
    public record Placement(String world, int x, int y, int z, BlockFace facing) {
    }

    /** Where the board is, or null. */
    public Placement placement() {
        return maps.isEmpty() ? null : placement;
    }

    // ------------------------------------------------------------------ set up

    /** Sets up the board on the wall of frames the player looks at. */
    public Result set(Player player) {
        Entity target = player.getTargetEntity(8);
        if (!(target instanceof ItemFrame frame)) {
            return Result.NO_FRAME;
        }
        BlockFace facing = frame.getFacing();
        if (facing.getModY() != 0) {
            return Result.NOT_ON_WALL;
        }
        // All frames of the wall around the one the player looks at (same facing, side by side).
        Map<Long, ItemFrame> wall = new HashMap<>();
        Location at = frame.getLocation();
        for (ItemFrame f : at.getWorld().getNearbyEntitiesByType(ItemFrame.class, at, 12)) {
            if (f.getFacing() == facing) {
                wall.put(key(f.getLocation().getBlockX(), f.getLocation().getBlockY(), f.getLocation().getBlockZ()), f);
            }
        }
        int rx = facing.getModZ();
        int rz = -facing.getModX();
        // Connected frames, in board coordinates (u to the right as seen by the player, v up).
        int bx = at.getBlockX();
        int by = at.getBlockY();
        int bz = at.getBlockZ();
        Map<Long, int[]> found = new HashMap<>();
        List<int[]> todo = new ArrayList<>(List.of(new int[]{0, 0}));
        while (!todo.isEmpty() && found.size() < 64) {
            int[] uv = todo.removeLast();
            long k = ((long) uv[0] << 32) ^ (uv[1] & 0xffffffffL);
            if (found.containsKey(k) || !wall.containsKey(key(bx + rx * uv[0], by + uv[1], bz + rz * uv[0]))) {
                continue;
            }
            found.put(k, uv);
            todo.add(new int[]{uv[0] + 1, uv[1]});
            todo.add(new int[]{uv[0] - 1, uv[1]});
            todo.add(new int[]{uv[0], uv[1] + 1});
            todo.add(new int[]{uv[0], uv[1] - 1});
        }
        int minU = found.values().stream().mapToInt(uv -> uv[0]).min().orElse(0);
        int maxU = found.values().stream().mapToInt(uv -> uv[0]).max().orElse(0);
        int minV = found.values().stream().mapToInt(uv -> uv[1]).min().orElse(0);
        int maxV = found.values().stream().mapToInt(uv -> uv[1]).max().orElse(0);
        lastSize = new int[]{maxU - minU + 1, maxV - minV + 1};
        if (lastSize[0] != COLUMNS || lastSize[1] != ROWS || found.size() != COLUMNS * ROWS) {
            return Result.WRONG_SIZE;
        }
        Location topLeft = new Location(at.getWorld(), bx + rx * minU, by + maxV, bz + rz * minU);
        return setAt(topLeft, facing, wall) ? Result.OK : Result.WRONG_SIZE;
    }

    /** Sets up the board with its top left frame at the block (used by the lobby template). */
    public boolean setAt(Location topLeft, BlockFace facing) {
        Map<Long, ItemFrame> wall = new HashMap<>();
        for (ItemFrame f : topLeft.getWorld().getNearbyEntitiesByType(ItemFrame.class, topLeft, 8)) {
            if (f.getFacing() == facing) {
                wall.put(key(f.getLocation().getBlockX(), f.getLocation().getBlockY(), f.getLocation().getBlockZ()), f);
            }
        }
        return setAt(topLeft, facing, wall);
    }

    private boolean setAt(Location topLeft, BlockFace facing, Map<Long, ItemFrame> wall) {
        int rx = facing.getModZ();
        int rz = -facing.getModX();
        List<ItemFrame> frames = new ArrayList<>();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                ItemFrame f = wall.get(key(topLeft.getBlockX() + rx * col, topLeft.getBlockY() - row,
                        topLeft.getBlockZ() + rz * col));
                if (f == null) {
                    return false;
                }
                frames.add(f);
            }
        }
        remove();
        World world = topLeft.getWorld();
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < frames.size(); i++) {
            MapView view = Bukkit.createMap(world);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);
            view.setLocked(true);
            attach(view, i);
            ItemStack item = new ItemStack(Material.FILLED_MAP);
            MapMeta meta = (MapMeta) item.getItemMeta();
            meta.setMapView(view);
            item.setItemMeta(meta);
            ItemFrame f = frames.get(i);
            f.setItem(item, false);
            f.setRotation(org.bukkit.Rotation.NONE);
            f.setFixed(true);
            f.setInvulnerable(true);
            f.getPersistentDataContainer().set(marker, PersistentDataType.INTEGER, i);
            ids.add(view.getId());
        }
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("world", world.getName());
        yml.set("x", topLeft.getBlockX());
        yml.set("y", topLeft.getBlockY());
        yml.set("z", topLeft.getBlockZ());
        yml.set("facing", facing.name());
        yml.set("maps", ids);
        placement = new Placement(world.getName(), topLeft.getBlockX(), topLeft.getBlockY(), topLeft.getBlockZ(), facing);
        sent.clear();
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save screen.yml", e);
        }
        return true;
    }

    /** Takes the board down: the frames are empty and free again. */
    public void remove() {
        Placement p = placement();
        if (p != null) {
            World world = Bukkit.getWorld(p.world());
            if (world != null) {
                Location at = new Location(world, p.x(), p.y(), p.z());
                for (ItemFrame f : world.getNearbyEntitiesByType(ItemFrame.class, at, 10)) {
                    if (f.getPersistentDataContainer().has(marker)) {
                        f.setItem(null, false);
                        f.setFixed(false);
                        f.setInvulnerable(false);
                        f.getPersistentDataContainer().remove(marker);
                    }
                }
            }
        }
        for (int id : maps.keySet()) {
            MapView view = Bukkit.getMap(id);
            if (view != null) {
                view.getRenderers().forEach(view::removeRenderer);
            }
        }
        maps.clear();
        views.clear();
        placement = null;
        sent.clear();
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete screen.yml");
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3ffffff) << 38) | ((long) (z & 0x3ffffff) << 12) | (y & 0xfff);
    }

    private void attach(MapView view, int piece) {
        view.getRenderers().forEach(view::removeRenderer);
        view.addRenderer(new Piece(piece));
        maps.put(view.getId(), piece);
        views.put(view.getId(), view);
    }

    /** One map: its 128 x 128 part of the board, per player, redrawn only when the picture changed. */
    private final class Piece extends MapRenderer {
        private final int column;
        private final int row;
        private final Map<UUID, Long> drawn = new ConcurrentHashMap<>();

        Piece(int index) {
            super(true);
            this.column = index % COLUMNS;
            this.row = index / COLUMNS;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void render(MapView map, MapCanvas canvas, Player player) {
            Board.Picture picture = board.picture(plugin.messages().languageOf(player));
            Long last = drawn.get(player.getUniqueId());
            if (last != null && last == picture.version()) {
                return;
            }
            drawn.put(player.getUniqueId(), picture.version());
            Pixels pixels = picture.pixels();
            int ox = column * 128;
            int oy = row * 128;
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    canvas.setPixel(x, y, pixels.get(ox + x, oy + y));
                }
            }
        }

        void forget(UUID player) {
            drawn.remove(player);
        }
    }

    // --------------------------------------------------------------- protection

    private boolean isScreen(Entity entity) {
        return entity instanceof ItemFrame f && f.getPersistentDataContainer().has(marker);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (isScreen(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(HangingBreakEvent event) {
        if (isScreen(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (isScreen(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sent.remove(event.getPlayer().getUniqueId());
        for (int id : maps.keySet()) {
            MapView view = Bukkit.getMap(id);
            if (view != null) {
                for (MapRenderer r : view.getRenderers()) {
                    if (r instanceof Piece piece) {
                        piece.forget(event.getPlayer().getUniqueId());
                    }
                }
            }
        }
    }
}
