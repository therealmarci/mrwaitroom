package at.marcelr.mrwaitroom.command;

import at.marcelr.mrwaitroom.MRwaitroom;
import at.marcelr.mrwaitroom.Messages;
import at.marcelr.mrwaitroom.UpdateChecker;
import at.marcelr.mrwaitroom.link.MainLink;
import at.marcelr.mrwaitroom.room.Display;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static at.marcelr.mrwaitroom.Messages.tag;

/** /mrwaitroom status | send &lt;player|all&gt; | reload | version. */
public final class WaitroomCommand implements TabExecutor {

    public static final String PERM_STATUS = "mrwaitroom.admin.status";
    public static final String PERM_SEND = "mrwaitroom.admin.send";
    public static final String PERM_RELOAD = "mrwaitroom.admin.reload";
    public static final String PERM_VERSION = "mrwaitroom.version";

    private final MRwaitroom plugin;

    public WaitroomCommand(MRwaitroom plugin) {
        this.plugin = plugin;
    }

    private Messages messages() {
        return plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        plugin.messages().run(sender, () -> {
            switch (sub) {
                case "status" -> {
                    if (check(sender, PERM_STATUS)) {
                        status(sender);
                    }
                }
                case "send" -> {
                    if (check(sender, PERM_SEND)) {
                        send(sender, args);
                    }
                }
                case "reload" -> {
                    if (check(sender, PERM_RELOAD)) {
                        plugin.reload();
                        messages().send(sender, "reload-done");
                    }
                }
                case "version", "ver" -> {
                    if (check(sender, PERM_VERSION)) {
                        version(sender);
                    }
                }
                default -> messages().send(sender, "usage");
            }
        });
        return true;
    }

    private boolean check(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        messages().send(sender, "no-permission");
        return false;
    }

    private void status(CommandSender sender) {
        MainLink.View view = plugin.link().view();
        long now = System.currentTimeMillis();
        String link = plugin.settings().main().linkUrl().isEmpty() ? messages().plain("status-link-none")
                : view.linkOk() ? messages().plain("status-link-ok")
                : messages().plain("status-link-failed").replace("<error>", view.linkError());
        messages().send(sender, "status",
                tag("phase", messages().plain("phase-" + view.phase().name().toLowerCase(Locale.ROOT))),
                tag("since", view.downSince() == 0 ? "-" : Display.clock(now - view.downSince())),
                tag("players", Bukkit.getOnlinePlayers().size()),
                tag("link", link),
                tag("port", messages().plain(view.portOpen() ? "status-port-open" : "status-port-closed")),
                tag("estimate", Display.clock(view.estimate())));
    }

    private void send(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages().send(sender, "usage");
            return;
        }
        List<Player> targets = new ArrayList<>();
        if (args[1].equalsIgnoreCase("all")) {
            targets.addAll(Bukkit.getOnlinePlayers());
        } else {
            Player player = Bukkit.getPlayerExact(args[1]);
            if (player == null) {
                messages().send(sender, "player-not-found", tag("player", args[1]));
                return;
            }
            targets.add(player);
        }
        targets.forEach(plugin.returner()::send);
        messages().send(sender, "send-done", tag("count", targets.size()));
    }

    private void version(CommandSender sender) {
        UpdateChecker updates = plugin.updateChecker();
        messages().send(sender, "version",
                tag("version", updates.currentVersion()), tag("mc", updates.minecraftVersion()),
                tag("author", String.join(", ", plugin.getPluginMeta().getAuthors())));
        Runnable links = () -> {
            sender.sendMessage(Component.empty());
            messages().send(sender, "version-links",
                    tag("website", link("Website", updates.websitePage())),
                    tag("github", link("GitHub", updates.githubPage())));
        };
        if (plugin.settings().update().enabled()) {
            updates.checkNow(sender, links);
        } else {
            updates.sendStatus(sender);
            links.run();
        }
    }

    private static Component link(String label, String url) {
        return Component.text(label).color(NamedTextColor.AQUA).decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission(PERM_STATUS)) {
                options.add("status");
            }
            if (sender.hasPermission(PERM_SEND)) {
                options.add("send");
            }
            if (sender.hasPermission(PERM_RELOAD)) {
                options.add("reload");
            }
            if (sender.hasPermission(PERM_VERSION)) {
                options.add("version");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("send") && sender.hasPermission(PERM_SEND)) {
            options.add("all");
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }
}
