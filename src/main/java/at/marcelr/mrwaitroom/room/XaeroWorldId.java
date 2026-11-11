package at.marcelr.mrwaitroom.room;

import at.marcelr.mrwaitroom.MRwaitroom;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRegisterChannelEvent;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Tells Xaero's Minimap and World Map the map ID of the waiting room (xaero.world-id). Behind a proxy the main server
 * and the waiting room have the same address; with an own ID Xaero keeps their maps apart instead of drawing the
 * empty waiting room over the main server. Same message as Xaero's own server side: channel {@code <mod>:main},
 * packet 0 (level map properties) with the ID as int.
 */
public final class XaeroWorldId implements Listener {

    private static final Set<String> CHANNELS = Set.of("xaerominimap:main", "xaeroworldmap:main");

    private final MRwaitroom plugin;

    public XaeroWorldId(MRwaitroom plugin) {
        this.plugin = plugin;
    }

    public void start() {
        for (String channel : CHANNELS) {
            if (!Bukkit.getMessenger().isOutgoingChannelRegistered(plugin, channel)) {
                Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, channel);
            }
        }
    }

    /** The client announces Xaero's channel - then it can receive the ID. */
    @EventHandler
    public void onRegister(PlayerRegisterChannelEvent event) {
        var xaero = plugin.settings().xaero();
        if (!xaero.enabled() || !CHANNELS.contains(event.getChannel())) {
            return;
        }
        byte[] payload = ByteBuffer.allocate(5).put((byte) 0).putInt(xaero.worldId()).array();
        event.getPlayer().sendPluginMessage(plugin, event.getChannel(), payload);
    }
}
