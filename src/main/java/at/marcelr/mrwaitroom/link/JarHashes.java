package at.marcelr.mrwaitroom.link;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SHA-1 of the JAR a plugin was loaded from (Modrinth finds project and version by it). Computed once per file state
 * (path, size, modified). The same class lives in MRadmin (versions/JarHashes) - keep both the same.
 */
public final class JarHashes {

    private record Known(long size, long modified, String sha1) {
    }

    private static final Method GET_FILE;

    static {
        Method m = null;
        try {
            m = JavaPlugin.class.getDeclaredMethod("getFile");
            m.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // without it no hashes - the plugins are then only matched by hand
        }
        GET_FILE = m;
    }

    private final Map<String, Known> cache = new ConcurrentHashMap<>();

    /** The JAR of a plugin, or null (not a JavaPlugin, not readable). */
    public static File file(Plugin plugin) {
        if (GET_FILE == null || !(plugin instanceof JavaPlugin)) {
            return null;
        }
        try {
            return GET_FILE.invoke(plugin) instanceof File f && f.isFile() ? f : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** The SHA-1 (hex) of the plugin's JAR, or null. */
    public String sha1(Plugin plugin) {
        File f = file(plugin);
        if (f == null) {
            return null;
        }
        String key = f.getAbsolutePath();
        Known known = cache.get(key);
        if (known != null && known.size() == f.length() && known.modified() == f.lastModified()) {
            return known.sha1();
        }
        try (InputStream in = Files.newInputStream(f.toPath())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) > 0) {
                digest.update(buffer, 0, n);
            }
            String sha1 = HexFormat.of().formatHex(digest.digest());
            cache.put(key, new Known(f.length(), f.lastModified(), sha1));
            return sha1;
        } catch (Exception e) {
            return null;
        }
    }
}
