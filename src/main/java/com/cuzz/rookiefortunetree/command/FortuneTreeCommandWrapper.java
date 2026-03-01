package com.cuzz.rookiefortunetree.command;

import com.cuzz.bukkitspring.api.annotation.Autowired;
import com.cuzz.bukkitspring.api.annotation.Component;
import com.cuzz.rookiefortunetree.config.FortuneTreeConfig;
import com.cuzz.rookiefortunetree.config.MenuIconConfig;
import com.cuzz.rookiefortunetree.service.FortuneTreeService;
import com.cuzz.starter.bukkitspring.time.api.TimeService;
import com.cuzz.rookiefortunetree.wrapper.FortuneTreeActionDispatcher;
import com.cuzz.rookiefortunetree.util.TextUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public final class FortuneTreeCommandWrapper implements CommandExecutor, TabCompleter {
    private final FortuneTreeActionDispatcher dispatcher;
    private final FortuneTreeService service;
    private final FortuneTreeConfig config;
    private final MenuIconConfig menuIconConfig;
    private final TimeService timeService;

    @Autowired
    public FortuneTreeCommandWrapper(FortuneTreeActionDispatcher dispatcher,
                                     FortuneTreeService service,
                                     FortuneTreeConfig config,
                                     MenuIconConfig menuIconConfig,
                                     @Autowired(required = false) TimeService timeService) {
        this.dispatcher = dispatcher;
        this.service = service;
        this.config = config;
        this.menuIconConfig = menuIconConfig;
        this.timeService = timeService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ("water".equals(name)) {
            if (sender instanceof Player player) {
                if (!player.hasPermission("dailywatertree.use")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                dispatcher.water(player, null);
            } else {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            }
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                if (!player.hasPermission("dailywatertree.gui")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                dispatcher.openGui(player);
            } else {
                sender.sendMessage(ChatColor.RED + "Only players can open GUI.");
            }
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "gui" -> {
                if (sender instanceof Player player) {
                    if (!player.hasPermission("dailywatertree.gui")) {
                        player.sendMessage(ChatColor.RED + "No permission.");
                        return true;
                    }
                    dispatcher.openGui(player);
                } else {
                    sender.sendMessage(ChatColor.RED + "Only players can open GUI.");
                }
            }
            case "water" -> {
                if (sender instanceof Player player) {
                    if (!player.hasPermission("dailywatertree.use")) {
                        player.sendMessage(ChatColor.RED + "No permission.");
                        return true;
                    }
                    Integer level = parseOptionalInt(args, 1);
                    dispatcher.water(player, level);
                } else {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                }
            }
            case "collect" -> {
                if (sender instanceof Player player) {
                    if (!player.hasPermission("dailywatertree.use")) {
                        player.sendMessage(ChatColor.RED + "No permission.");
                        return true;
                    }
                    dispatcher.collectAll(player);
                } else {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                }
            }
            case "reload" -> {
                if (!sender.hasPermission("dailywatertree.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                config.reload();
                menuIconConfig.reload();
                sender.sendMessage(TextUtil.colorize("&a[FortuneTree] config reloaded."));
            }
            case "debug" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                    return true;
                }
                if (!player.hasPermission("dailywatertree.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                service.debug(player);
            }
            case "timeoffset" -> {
                if (!sender.hasPermission("dailywatertree.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (timeService == null || !timeService.isEnabled()) {
                    sender.sendMessage(TextUtil.colorize("&c[FortuneTree] TimeService is not available."));
                    return true;
                }
                if (args.length == 1) {
                    long currentHours = timeService.debugOffset().toHours();
                    sender.sendMessage(TextUtil.colorize("&a[FortuneTree] Current time offset: &e" + currentHours + "h"));
                    sender.sendMessage(TextUtil.colorize("&7Usage: /wt timeoffset <deltaHours> | /wt timeoffset set <hours>"));
                    return true;
                }
                Long currentHours = timeService.debugOffset().toHours();
                Long targetHours;
                if ("set".equalsIgnoreCase(args[1])) {
                    Long absoluteHours = parseOptionalLong(args, 2);
                    if (absoluteHours == null) {
                        sender.sendMessage(TextUtil.colorize("&cUsage: /wt timeoffset set <hours>"));
                        return true;
                    }
                    targetHours = absoluteHours;
                } else {
                    Long deltaHours = parseOptionalLong(args, 1);
                    if (deltaHours == null) {
                        sender.sendMessage(TextUtil.colorize("&cUsage: /wt timeoffset <deltaHours>"));
                        sender.sendMessage(TextUtil.colorize("&7Example: /wt timeoffset 24  (add 24h)"));
                        return true;
                    }
                    targetHours = currentHours + deltaHours;
                }
                timeService.setDebugOffset(Duration.ofHours(targetHours));
                long appliedHours = timeService.debugOffset().toHours();
                sender.sendMessage(TextUtil.colorize("&a[FortuneTree] Time offset: &e" + currentHours + "h &a-> &e" + appliedHours + "h"));
            }
            default -> sender.sendMessage(TextUtil.colorize("&eUsage: /wt [gui|water|collect|reload|debug|timeoffset <deltaHours>|timeoffset set <hours>]"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!"wt".equalsIgnoreCase(command.getName())) {
            return result;
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String option : List.of("gui", "water", "collect", "reload", "debug", "timeoffset")) {
                if (option.startsWith(prefix)) {
                    result.add(option);
                }
            }
            return result;
        }
        if (args.length == 2 && "water".equalsIgnoreCase(args[0]) && sender.hasPermission("dailywatertree.admin")) {
            String prefix = args[1];
            for (int i = 1; i <= 6; i++) {
                String s = String.valueOf(i);
                if (s.startsWith(prefix)) {
                    result.add(s);
                }
            }
        }
        if ("timeoffset".equalsIgnoreCase(args[0]) && sender.hasPermission("dailywatertree.admin")) {
            if (args.length == 2) {
                String prefix = args[1];
                for (String suggestion : List.of("24", "-24", "1", "-1", "set")) {
                    if (suggestion.startsWith(prefix)) {
                        result.add(suggestion);
                    }
                }
            } else if (args.length == 3 && "set".equalsIgnoreCase(args[1])) {
                String prefix = args[2];
                for (String suggestion : List.of("0", "24", "48", "-24")) {
                    if (suggestion.startsWith(prefix)) {
                        result.add(suggestion);
                    }
                }
            }
        }
        return result;
    }

    private Integer parseOptionalInt(String[] args, int index) {
        if (args == null || index < 0 || index >= args.length) {
            return null;
        }
        String text = args[index];
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long parseOptionalLong(String[] args, int index) {
        if (args == null || index < 0 || index >= args.length) {
            return null;
        }
        String text = args[index];
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
