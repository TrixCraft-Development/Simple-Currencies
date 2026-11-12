package de.nitrox.SimpleCurrencies;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.DoubleArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CurrencySystemManager {

    private final SimpleCurrencies plugin;
    private final Map<String, CurrencySystemInstance> systems = new HashMap<>();

    public CurrencySystemManager(SimpleCurrencies plugin) {
        this.plugin = plugin;
    }

    public void loadSystems() {
        systems.clear();

        File folder = plugin.getDataFolder();
        if (!folder.exists()) folder.mkdirs();

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        // Only create default.yml on first run / when the folder has no .yml files
        if (files.length == 0) {
            plugin.saveResource("default.yml", false);
            files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files == null) return;
        }

        for (File file : files) {
            String id = file.getName().replace(".yml", "");

            CurrencySystemInstance system =
                    new CurrencySystemInstance(plugin, id, id, file);

            systems.put(id.toLowerCase(), system);
            registerCurrencyCommand(system);

            plugin.getLogger().info("Loaded currency: " + id);
        }
    }

    private void registerCurrencyCommand(CurrencySystemInstance system) {

        new CommandAPICommand(system.getCommand())
                .executes((sender, args) -> {
                    if (sender instanceof Player player) {
                        sender.sendMessage(
                                ChatColor.GREEN + "Balance: " + system.getBalanceFormatted(player.getUniqueId())
                        );
                    } else {
                        sender.sendMessage(ChatColor.GREEN + "=== " + ChatColor.GRAY + "SimpleCurrencies Commands for " + system.getCommand() + ChatColor.GREEN + " ===");
                        sender.sendMessage(ChatColor.GRAY + "/" + system.getCommand() + " give <player> <amount>");
                        sender.sendMessage(ChatColor.GRAY + "/" + system.getCommand() + " remove <player> <amount>");
                        sender.sendMessage(ChatColor.GRAY + "/" + system.getCommand() + " balance [player]");
                        sender.sendMessage(ChatColor.GRAY + "/" + system.getCommand() + " wipe");
                        sender.sendMessage(ChatColor.GRAY + "/" + system.getCommand() + " transfer <system> [rate]");
                    }
                })
                .withSubcommand(new CommandAPICommand("give")
                        .withPermission("simplecurrencies.give")
                        .withArguments(
                                new EntitySelectorArgument.OnePlayer("player"),
                                new DoubleArgument("amount")
                        )
                        .executes((sender, args) -> {
                            Player target = (Player) args.get("player");
                            double amount = (double) args.get("amount");

                            system.add(
                                    target.getUniqueId(),
                                    target.getName(),
                                    amount
                            );
                            system.save();
                            sender.sendMessage(ChatColor.GREEN + "Added " + system.formatNumber(amount) + " to " + target.getName()
                            );
                        })
                )
                .withSubcommand(new CommandAPICommand("remove")
                        .withPermission("simplecurrencies.remove")
                        .withArguments(
                                new EntitySelectorArgument.OnePlayer("player"),
                                new DoubleArgument("amount")
                        )
                        .executes((sender, args) -> {
                            Player target = (Player) args.get("player");
                            double amount = (double) args.get("amount");

                            system.remove(
                                    target.getUniqueId(),
                                    target.getName(),
                                    amount
                            );
                            system.save();
                            sender.sendMessage(ChatColor.GREEN + "Removed " + system.formatNumber(amount) + " from " + target.getName()
                            );
                        })
                )
                .withSubcommand(new CommandAPICommand("balance")
                        .withOptionalArguments(new EntitySelectorArgument.OnePlayer("player"))
                        .executes((sender, args) -> {
                            Player target = (Player) args.get("player");

                            if (target == null) {
                                if (!(sender instanceof Player)) {
                                    sender.sendMessage(ChatColor.RED + "You must specify a player.");
                                    return;
                                }
                                target = (Player) sender;
                            }

                            sender.sendMessage(
                                    ChatColor.GREEN + "Balance: " + system.getBalanceString(target.getUniqueId())
                            );
                        })
                )
                .withSubcommand(new CommandAPICommand("wipe")
                        .withPermission("simplecurrencies.wipe")
                        .executes((sender, args) -> {
                            system.wipe();
                            system.save();
                            sender.sendMessage(ChatColor.RED + "Currency wiped.");
                        })
                )
                .withSubcommand(new CommandAPICommand("transfer")
                        .withPermission("simplecurrencies.transfer")
                        .withArguments(
                                new StringArgument("targetCurrency")
                                        .replaceSuggestions(
                                                ArgumentSuggestions.stringCollection(
                                                        info -> systems.keySet().stream()
                                                                .filter(id -> !id.equalsIgnoreCase(system.getId()))
                                                                .toList()
                                                )
                                        )
                        )
                        .withOptionalArguments(new DoubleArgument("rate"))
                        .executes((sender, args) -> {
                            String targetId = (String) args.get("targetCurrency");
                            CurrencySystemInstance target = systems.get(targetId.toLowerCase());

                            if (target == null) {
                                sender.sendMessage(ChatColor.RED + "Unknown currency.");
                                return;
                            }

                            if (target.getId().equalsIgnoreCase(system.getId())) {
                                sender.sendMessage(ChatColor.RED + "Cannot transfer a currency to itself.");
                                return;
                            }

                            Double rateArg = (Double) args.get("rate");
                            double rate;
                            if (rateArg != null) {
                                if (!CurrencySystemInstance.isValidRate(rateArg)) {
                                    sender.sendMessage(ChatColor.RED + "Exchange rate must be greater than 0.");
                                    return;
                                }
                                rate = rateArg;
                            } else {
                                rate = system.getExchangeRate(target.getId());
                            }

                            system.transferTo(target, rate);
                            sender.sendMessage(
                                    ChatColor.GREEN + "Transferred " + system.getId() + " to " + target.getId()
                                            + " (rate: 1 " + system.getId() + " = "
                                            + CurrencySystemInstance.formatRate(rate) + " " + target.getId() + ")."
                            );
                        })
                )
                .register();
    }

    public CurrencySystemInstance get(String id) {
        return systems.get(id.toLowerCase());
    }

    public Map<String, CurrencySystemInstance> getAll() {
        return systems;
    }
}
