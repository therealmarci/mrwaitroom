package at.marcelr.mrwaitroom.room;

import at.marcelr.mrwaitroom.MRwaitroom;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * The lobby a new waiting room world starts with: a structure (blocks and item frames) plus where the spawn is and
 * where the board hangs. Comes with the plugin; {@code /mrwaitroom lobby save} stores the current build of the
 * waiting room in plugins/MRwaitroom/lobby/ - that one then wins over the bundled one.
 */
public final class Lobby {

    /** What was saved: size and the area. */
    public record Saved(int width, int height, int length, int[] min, int[] max) {
    }

    private final MRwaitroom plugin;
    private final File folder;

    public Lobby(MRwaitroom plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "lobby");
    }

    // -------------------------------------------------------------------- save

    /**
     * Saves the build of the world. Without an area it is found by itself: every non-air block in the loaded chunks
     * (the lobby is near the spawn, where the player stands). Calls {@code done} on the main thread (null = no
     * blocks found).
     */
    public void save(World world, int[] from, int[] to, Consumer<Saved> done) {
        if (from != null) {
            done.accept(write(world, min(from, to), max(from, to)));
            return;
        }
        List<ChunkSnapshot> snapshots = new ArrayList<>();
        for (Chunk chunk : world.getLoadedChunks()) {
            snapshots.add(chunk.getChunkSnapshot(true, false, false));
        }
        int minH = world.getMinHeight();
        int maxH = world.getMaxHeight();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
            int[] hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            for (ChunkSnapshot s : snapshots) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int top = Math.min(maxH - 1, s.getHighestBlockYAt(x, z) + 1);
                        while (top >= minH && s.getBlockType(x, top, z).isAir()) {
                            top--;
                        }
                        if (top < minH) {
                            continue;
                        }
                        int low = minH;
                        while (low < top && s.getBlockType(x, low, z).isAir()) {
                            low++;
                        }
                        int wx = s.getX() * 16 + x;
                        int wz = s.getZ() * 16 + z;
                        lo[0] = Math.min(lo[0], wx);
                        lo[1] = Math.min(lo[1], low);
                        lo[2] = Math.min(lo[2], wz);
                        hi[0] = Math.max(hi[0], wx);
                        hi[1] = Math.max(hi[1], top);
                        hi[2] = Math.max(hi[2], wz);
                    }
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> done.accept(lo[0] == Integer.MAX_VALUE ? null : write(world, lo, hi)));
        });
    }

    private Saved write(World world, int[] lo, int[] hi) {
        StructureManager manager = Bukkit.getStructureManager();
        Structure structure = manager.createStructure();
        BlockVector size = new BlockVector(hi[0] - lo[0] + 1, hi[1] - lo[1] + 1, hi[2] - lo[2] + 1);
        structure.fill(new Location(world, lo[0], lo[1], lo[2]), size, true);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create " + folder);
        }
        try {
            manager.saveStructure(new File(folder, "lobby.nbt"), structure);
            YamlConfiguration yml = new YamlConfiguration();
            yml.options().setHeader(List.of("Lobby of MRwaitroom - placed when a new waiting room world is created.",
                    "Saved with /mrwaitroom lobby save. Delete this folder to get the bundled lobby back."));
            yml.set("origin", List.of(lo[0], lo[1], lo[2]));
            Location spawn = world.getSpawnLocation();
            yml.set("spawn.x", spawn.getX());
            yml.set("spawn.y", spawn.getY());
            yml.set("spawn.z", spawn.getZ());
            yml.set("spawn.yaw", (double) spawn.getYaw());
            yml.set("spawn.pitch", (double) spawn.getPitch());
            Screen.Placement screen = plugin.screen().placement();
            if (screen != null && screen.world().equals(world.getName())) {
                yml.set("screen.x", screen.x());
                yml.set("screen.y", screen.y());
                yml.set("screen.z", screen.z());
                yml.set("screen.facing", screen.facing().name());
            }
            yml.save(new File(folder, "lobby.yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save the lobby", e);
            return null;
        }
        plugin.getLogger().info("Saved the lobby (" + size.getBlockX() + " x " + size.getBlockY() + " x "
                + size.getBlockZ() + ") to " + folder + ".");
        return new Saved(size.getBlockX(), size.getBlockY(), size.getBlockZ(), lo, hi);
    }

    private static int[] min(int[] a, int[] b) {
        return new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])};
    }

    private static int[] max(int[] a, int[] b) {
        return new int[]{Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
    }

    // ------------------------------------------------------------------- place

    /**
     * Builds the lobby into a new world and sets the spawn; the board is set up a moment later (its frames have to
     * be in the world first). False if there is no lobby to place.
     */
    public boolean place(World world) {
        StructureManager manager = Bukkit.getStructureManager();
        File ownNbt = new File(folder, "lobby.nbt");
        File ownYml = new File(folder, "lobby.yml");
        Structure structure;
        YamlConfiguration yml;
        try {
            if (ownNbt.isFile() && ownYml.isFile()) {
                structure = manager.loadStructure(ownNbt);
                yml = YamlConfiguration.loadConfiguration(ownYml);
            } else {
                try (InputStream nbt = plugin.getResource("lobby/lobby.nbt");
                     InputStream meta = plugin.getResource("lobby/lobby.yml")) {
                    if (nbt == null || meta == null) {
                        return false;
                    }
                    structure = manager.loadStructure(nbt);
                    yml = YamlConfiguration.loadConfiguration(new InputStreamReader(meta, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read the lobby - building the platform instead", e);
            return false;
        }
        List<Integer> origin = yml.getIntegerList("origin");
        if (origin.size() != 3) {
            return false;
        }
        structure.place(new Location(world, origin.get(0), origin.get(1), origin.get(2)), true, StructureRotation.NONE,
                Mirror.NONE, 0, 1f, new Random());
        world.setSpawnLocation(new Location(world, yml.getDouble("spawn.x"), yml.getDouble("spawn.y"),
                yml.getDouble("spawn.z"), (float) yml.getDouble("spawn.yaw"), (float) yml.getDouble("spawn.pitch")));
        if (yml.isConfigurationSection("screen")) {
            Location topLeft = new Location(world, yml.getInt("screen.x"), yml.getInt("screen.y"), yml.getInt("screen.z"));
            BlockFace facing;
            try {
                facing = BlockFace.valueOf(yml.getString("screen.facing", "NORTH"));
            } catch (IllegalArgumentException e) {
                facing = BlockFace.NORTH;
            }
            BlockFace f = facing;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                topLeft.getChunk().load();
                if (!plugin.screen().setAt(topLeft, f)) {
                    plugin.getLogger().warning("The board of the lobby was not found - set it up with /mrwaitroom screen set.");
                }
            }, 40L);
        }
        plugin.getLogger().info("Built the lobby of the waiting room.");
        return true;
    }
}
