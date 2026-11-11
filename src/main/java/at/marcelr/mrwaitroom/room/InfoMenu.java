package at.marcelr.mrwaitroom.room;

import at.marcelr.mrwaitroom.MRwaitroom;
import at.marcelr.mrwaitroom.Messages;
import at.marcelr.mrwaitroom.link.MainLink;
import at.marcelr.mrwaitroom.link.MainStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static at.marcelr.mrwaitroom.Messages.tag;

/**
 * The menu behind the clock (right click): everything about the main server at a glance. Symmetric, 3 rows:
 * <pre>
 *  . . . . S . . . .     S = state
 *  . R . T . M . W .     R = reason, T = times, M = maintenance, W = warnings
 *  . . . B . C . . .     B = back to the server (grey while not ready), C = close
 * </pre>
 * Renewed every second while it is open ({@link Display}).
 */
public final class InfoMenu implements InventoryHolder, Listener {

    private static final int STATE = 4;
    private static final int REASON = 10;
    private static final int TIME = 12;
    private static final int MAINTENANCE = 14;
    private static final int ALERTS = 16;
    private static final int BACK = 21;
    private static final int CLOSE = 23;

    private final MRwaitroom plugin;
    private final Player viewer;
    private Inventory inventory;

    /** The listener instance (no viewer). */
    public InfoMenu(MRwaitroom plugin) {
        this(plugin, null);
    }

    private InfoMenu(MRwaitroom plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static void open(MRwaitroom plugin, Player player) {
        InfoMenu menu = new InfoMenu(plugin, player);
        Messages m = plugin.messages();
        menu.inventory = Bukkit.createInventory(menu, 27, m.as(player, () -> m.get("menu-title")));
        menu.render();
        player.openInventory(menu.inventory);
    }

    /** Fills the menu with the current state (in the viewer's language). */
    public void render() {
        Messages m = plugin.messages();
        MainLink.View view = plugin.link().view();
        Display.Line line = plugin.display().line(view, System.currentTimeMillis());
        TagResolver[] tags = line.tags();
        MainStatus status = view.status();
        m.run(viewer, () -> {
            ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, filler);
            }
            inventory.setItem(STATE, item(plugin.settings().display().item(), m.item(line.item(), tags),
                    List.of(m.get(line.bar(), tags).decoration(TextDecoration.ITALIC, false))));
            inventory.setItem(REASON, item(Material.WRITABLE_BOOK, m.item("menu-reason-name", tags),
                    m.lore("menu-reason-lore", tags)));
            inventory.setItem(TIME, item(Material.CLOCK, m.item("menu-time-name", tags), m.lore("menu-time-lore", tags)));
            boolean maintenance = status != null && status.maintenance() != null;
            inventory.setItem(MAINTENANCE, item(Material.SMITHING_TABLE, m.item("menu-maintenance-name", tags),
                    m.lore(maintenance ? (status.maintenanceActive() ? "menu-maintenance-lore" : "menu-maintenance-planned")
                            : "menu-maintenance-none", tags)));
            List<Component> alerts = new ArrayList<>();
            if (status != null) {
                for (Map<String, String> alert : status.alerts()) {
                    alerts.add(m.item("menu-alert-line", tag("title", MainStatus.in(alert, m.languageOf(viewer)))));
                }
            }
            if (alerts.isEmpty()) {
                alerts.addAll(m.lore("menu-alerts-none"));
            }
            inventory.setItem(ALERTS, item(Material.BELL, m.item("menu-alerts-name"), alerts));
            boolean ready = plugin.returner().mayGo(viewer, view);
            inventory.setItem(BACK, ready
                    ? item(Material.LIME_DYE, m.item("menu-back-name"), m.lore("menu-back-lore"))
                    : item(Material.GRAY_DYE, m.item("menu-back-locked-name"), m.lore("menu-back-locked-lore")));
            inventory.setItem(CLOSE, item(Material.BARRIER, m.item("menu-close"), List.of()));
        });
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setHideTooltip(material == Material.GRAY_STAINED_GLASS_PANE);
        item.setItemMeta(meta);
        return item;
    }

    // ---------------------------------------------------------------- listener

    /** Right click with the clock opens the menu. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack hand = event.getItem();
        if (Display.isBackItem(hand)) {
            event.setCancelled(true);
            plugin.returner().goBack(event.getPlayer());
            return;
        }
        if (hand == null || hand.getType() != plugin.settings().display().item()) {
            return;
        }
        event.setCancelled(true);
        open(plugin, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof InfoMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != menu.inventory) {
            return;
        }
        if (event.getSlot() == CLOSE) {
            player.closeInventory();
        } else if (event.getSlot() == BACK && plugin.returner().mayGo(player, plugin.link().view())) {
            player.closeInventory();
            plugin.returner().send(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof InfoMenu) {
            event.setCancelled(true);
        }
    }

    /** Renews the open menus (called every second by {@link Display}). */
    public static void refreshOpen() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof InfoMenu menu) {
                menu.render();
            }
        }
    }
}
