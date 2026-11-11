package at.marcelr.mrwaitroom.room;

import at.marcelr.mrwaitroom.MRwaitroom;
import at.marcelr.mrwaitroom.link.MainStatus;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Who may do what here - the table waiting-room.rights of MRadmin on the main server (it comes with the status
 * answer and is kept while the main server is down). Without an answer the defaults of MRadmin apply: supporters and
 * staff nothing, admins only in admin mode (admin mode here always).
 * <p>
 * The role is the highest of the permissions mradmin.groups.admin|staff|support - with LuckPerms on a shared
 * database the same as on the main server. Normal players have none of the rights.
 */
public final class Rights {

    public enum Role {
        ADMIN("admin", "mradmin.groups.admin"),
        STAFF("staff", "mradmin.groups.staff"),
        SUPPORT("support", "mradmin.groups.support");

        final String key;
        final String permission;

        Role(String key, String permission) {
            this.key = key;
            this.permission = permission;
        }
    }

    public enum Right {
        /** During a maintenance on the main server instead of waiting here. */
        MAINTENANCE("maintenance"),
        /** Come here while nothing is going on (MRadmin's menu) - decided over there. */
        VISIT("visit"),
        /** Stay here: not sent back by itself. */
        STAY("stay"),
        /** Switch the admin mode on here (item in the hotbar). */
        ADMIN_MODE("admin-mode");

        final String key;

        Right(String key) {
            this.key = key;
        }
    }

    private final MRwaitroom plugin;

    public Rights(MRwaitroom plugin) {
        this.plugin = plugin;
    }

    /** The highest team role, null for normal players. */
    public static Role role(Player player) {
        for (Role role : Role.values()) {
            if (player.hasPermission(role.permission)) {
                return role;
            }
        }
        return null;
    }

    /** never / always / admin-mode for the role (the defaults of MRadmin if the table does not say). */
    static String value(MainStatus status, Role role, Right right) {
        if (role == null) {
            return "never";
        }
        Map<String, String> row = status == null ? null : status.rights().get(role.key);
        String value = row == null ? null : row.get(right.key);
        if (value == null || !(value.equals("never") || value.equals("always") || value.equals("admin-mode"))) {
            return role != Role.ADMIN ? "never" : right == Right.ADMIN_MODE ? "always" : "admin-mode";
        }
        return value;
    }

    /** Whether the player may use the admin mode here at all. */
    public boolean mayUseAdminMode(Player player) {
        return !value(plugin.link().view().status(), role(player), Right.ADMIN_MODE).equals("never");
    }

    /** Whether the player has the right right now (with "admin-mode" only while in admin mode here). */
    public boolean allows(Player player, Right right) {
        return switch (value(plugin.link().view().status(), role(player), right)) {
            case "always" -> true;
            case "admin-mode" -> plugin.adminMode().on(player);
            default -> false;
        };
    }

    /** Whether the player may go back by hand (item, menu): stays here by right, or may be there during maintenance. */
    public boolean mayGoByHand(Player player) {
        return allows(player, Right.STAY) || allows(player, Right.MAINTENANCE);
    }
}
