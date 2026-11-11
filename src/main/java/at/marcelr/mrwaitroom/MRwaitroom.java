package at.marcelr.mrwaitroom;

import at.marcelr.mrwaitroom.command.MainCommand;
import at.marcelr.mrwaitroom.command.WaitroomCommand;
import at.marcelr.mrwaitroom.link.MainLink;
import at.marcelr.mrwaitroom.room.Display;
import at.marcelr.mrwaitroom.room.Returner;
import at.marcelr.mrwaitroom.room.Room;
import at.marcelr.mrwaitroom.room.XaeroWorldId;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Objects;

/**
 * Main class of MRwaitroom: a waiting room behind a proxy. Players wait here while the main server restarts, is
 * stopped or in maintenance, see how long it takes and go back by themselves. Works on its own; with MRadmin on the
 * main server (its section waiting-room) it also knows the reason, the countdown and the maintenance.
 */
public final class MRwaitroom extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private Settings settings;
    private final Messages messages = new Messages(1, Map.of());
    private final MainLink link = new MainLink(this);
    private final Room room = new Room(this);
    private final Display display = new Display(this);
    private final Returner returner = new Returner(this);
    private final XaeroWorldId xaero = new XaeroWorldId(this);
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        ConfigUpdater.update(this);
        loadConfiguration();
        if (settings.xaero().worldId() == 0) {
            int id = 0;
            while (id == 0) {
                id = new SecureRandom().nextInt();
            }
            ConfigUpdater.set(this, "xaero.world-id", id);
            loadConfiguration();
        }
        room.load();
        updateChecker = new UpdateChecker(this);
        updateChecker.start();

        var pm = getServer().getPluginManager();
        pm.registerEvents(room, this);
        pm.registerEvents(display, this);
        pm.registerEvents(returner, this);
        pm.registerEvents(xaero, this);
        pm.registerEvents(this, this);
        register("mrwaitroom", new WaitroomCommand(this));
        register("main", new MainCommand(this));

        link.start();
        display.start();
        returner.start();
        xaero.start();
        if (settings.main().linkUrl().isEmpty()) {
            getLogger().info("No main.link-url set - the waiting room only sees whether the main server is up.");
        } else if (settings.main().linkKey().isEmpty()) {
            getLogger().warning("main.link-key is empty - copy waiting-room.link.key from MRadmin's config.yml.");
        }
    }

    @Override
    public void onDisable() {
        link.stop();
        display.stop();
        returner.stop();
        if (updateChecker != null) {
            updateChecker.stop();
        }
    }

    /** Reloads config.yml and the texts and restarts the watching of the main server. */
    public void reload() {
        ConfigUpdater.update(this);
        loadConfiguration();
        link.start();
        display.start();
        returner.start();
        updateChecker.start();
    }

    private void loadConfiguration() {
        config = ConfigUpdater.load(this);
        settings = Settings.from(config, getLogger());
        messages.load(this, config.getString("general.language", "auto"), config.getString("general.fallback-language", "en"),
                config.getString("general.server-language", "en"));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updateChecker.notifyIfOutdated(event.getPlayer());
    }

    /** config.yml is read by {@link ConfigUpdater#load}, which also tolerates the UTF-8 BOM. */
    @Override
    public FileConfiguration getConfig() {
        if (config == null) {
            config = ConfigUpdater.load(this);
        }
        return config;
    }

    @Override
    public void reloadConfig() {
        config = ConfigUpdater.load(this);
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), name);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public MainLink link() {
        return link;
    }

    public Room room() {
        return room;
    }

    public Returner returner() {
        return returner;
    }

    public UpdateChecker updateChecker() {
        return updateChecker;
    }
}
