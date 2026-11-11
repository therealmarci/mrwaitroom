package at.marcelr.mrwaitroom.command;

import at.marcelr.mrwaitroom.MRwaitroom;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/** /main - back to the main server right away, if it is ready (otherwise it happens by itself anyway). */
public final class MainCommand implements TabExecutor {

    public static final String PERM = "mrwaitroom.main";

    private final MRwaitroom plugin;

    public MainCommand(MRwaitroom plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var messages = plugin.messages();
        messages.run(sender, () -> {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "players-only");
            } else if (!sender.hasPermission(PERM)) {
                messages.send(sender, "no-permission");
            } else if (!plugin.returner().mayGo(player, plugin.link().view())) {
                messages.send(sender, "main-not-now");
            } else {
                plugin.returner().send(player);
            }
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
