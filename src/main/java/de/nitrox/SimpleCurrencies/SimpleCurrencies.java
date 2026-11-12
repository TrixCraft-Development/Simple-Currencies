package de.nitrox.SimpleCurrencies;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIPaperConfig;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.DoubleArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class SimpleCurrencies extends JavaPlugin {

    private CurrencySystemManager manager;

    @Override
    public void onLoad() {
        CommandAPI.onLoad(
                new CommandAPIPaperConfig(this)
                        .verboseOutput(false)
        );
    }

    @Override
    public void onEnable() {
        CommandAPI.onEnable();

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        manager = new CurrencySystemManager(this);
        manager.loadSystems();

        registerMainCommand();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CurrencyPlaceholder(this).register();
            getLogger().info("PlaceholderAPI found — CurrencyPlaceholder registered.");
        } else {
            getLogger().info("PlaceholderAPI not found — placeholders unavailable.");
        }

        Bukkit.getPluginManager().registerEvents(
                new PlayerListener(this),
                this
        );

        getLogger().info(
                "SimpleCurrencies enabled — loaded "
                        + manager.getAll().size()
                        + " currency system(s)."
        );
    }

    private void registerMainCommand() {

        new CommandAPICommand("simplecurrencies")
                .withAliases("sc")
                .executes((sender, args) -> { sendHelp(sender); })

                // /sc reload <currency|all>
                .withSubcommand(
                        new CommandAPICommand("reload")
                                .withPermission("simplecurrencies.reload")
                                .withArguments(currencyArgument("currency"))
                                .executes((sender, args) -> {
                                    String id = (String) args.get("currency");

                                    if (id.equalsIgnoreCase("all")) {
                                        manager.loadSystems();
                                        sender.sendMessage(
                                                ChatColor.GREEN + "SimpleCurrencies: All systems reloaded."
                                        );
                                        return;
                                    }

                                    if (manager.get(id) == null) {
                                        sender.sendMessage(
                                                ChatColor.RED + "Unknown currency: " + id
                                        );
                                        return;
                                    }

                                    manager.loadSystems();
                                    sender.sendMessage(
                                            ChatColor.GREEN + "Currency reloaded: " + id
                                    );
                                })
                )

                // /sc rate <set|get|remove|list>
                .withSubcommand(buildRateCommand())

                // /sc help
                .withSubcommand(
                        new CommandAPICommand("help")
                                .executes((sender, args) -> { sendHelp(sender); })
                )

                .register();
    }

    private CommandAPICommand buildRateCommand() {
        return new CommandAPICommand("rate")
                .withPermission("simplecurrencies.rate")
                .executes((sender, args) -> { sendRateHelp(sender); })
                .withSubcommand(
                        new CommandAPICommand("set")
                                .withArguments(
                                        currencyArgument("from"),
                                        currencyArgument("to"),
                                        new DoubleArgument("rate")
                                )
                                .executes((sender, args) -> {
                                    CurrencySystemInstance from = resolveCurrency(sender, (String) args.get("from"));
                                    if (from == null) return;

                                    CurrencySystemInstance to = resolveCurrency(sender, (String) args.get("to"));
                                    if (to == null) return;

                                    if (from.getId().equalsIgnoreCase(to.getId())) {
                                        sender.sendMessage(ChatColor.RED + "Source and target currency must be different.");
                                        return;
                                    }

                                    double rate = (double) args.get("rate");
                                    if (!CurrencySystemInstance.isValidRate(rate)) {
                                        sender.sendMessage(ChatColor.RED + "Exchange rate must be greater than 0.");
                                        return;
                                    }

                                    from.setExchangeRate(to.getId(), rate);
                                    sender.sendMessage(
                                            ChatColor.GREEN + "Set exchange rate: 1 " + from.getId()
                                                    + " = " + CurrencySystemInstance.formatRate(rate) + " " + to.getId()
                                    );
                                })
                )
                .withSubcommand(
                        new CommandAPICommand("get")
                                .withArguments(
                                        currencyArgument("from"),
                                        currencyArgument("to")
                                )
                                .executes((sender, args) -> {
                                    CurrencySystemInstance from = resolveCurrency(sender, (String) args.get("from"));
                                    if (from == null) return;

                                    CurrencySystemInstance to = resolveCurrency(sender, (String) args.get("to"));
                                    if (to == null) return;

                                    Double configured = from.getConfiguredExchangeRate(to.getId());
                                    if (configured == null) {
                                        sender.sendMessage(
                                                ChatColor.YELLOW + "No custom exchange rate set for "
                                                        + from.getId() + " -> " + to.getId() + " (defaults to 1)."
                                        );
                                        return;
                                    }

                                    sender.sendMessage(
                                            ChatColor.GREEN + "Exchange rate: 1 " + from.getId()
                                                    + " = " + CurrencySystemInstance.formatRate(configured) + " " + to.getId()
                                    );
                                })
                )
                .withSubcommand(
                        new CommandAPICommand("remove")
                                .withArguments(
                                        currencyArgument("from"),
                                        currencyArgument("to")
                                )
                                .executes((sender, args) -> {
                                    CurrencySystemInstance from = resolveCurrency(sender, (String) args.get("from"));
                                    if (from == null) return;

                                    String toId = (String) args.get("to");
                                    if (!from.removeExchangeRate(toId)) {
                                        sender.sendMessage(
                                                ChatColor.RED + "No exchange rate found for " + from.getId() + " -> " + toId + "."
                                        );
                                        return;
                                    }

                                    sender.sendMessage(
                                            ChatColor.GREEN + "Removed exchange rate for " + from.getId() + " -> " + toId + "."
                                    );
                                })
                )
                .withSubcommand(
                        new CommandAPICommand("list")
                                .withOptionalArguments(currencyArgument("currency"))
                                .executes((sender, args) -> {
                                    String currencyId = (String) args.get("currency");
                                    if (currencyId != null) {
                                        CurrencySystemInstance system = resolveCurrency(sender, currencyId);
                                        if (system == null) return;

                                        if (system.getExchangeRates().isEmpty()) {
                                            sender.sendMessage(ChatColor.YELLOW + "No exchange rates configured for " + system.getId() + ".");
                                            return;
                                        }

                                        sender.sendMessage(ChatColor.GREEN + "=== " + ChatColor.GRAY + "Exchange Rates: " + system.getId() + ChatColor.GREEN + " ===");
                                        sendRatesForCurrency(sender, system);
                                        return;
                                    }

                                    boolean any = false;
                                    for (CurrencySystemInstance system : manager.getAll().values()) {
                                        if (system.getExchangeRates().isEmpty()) continue;
                                        if (!any) {
                                            sender.sendMessage(ChatColor.GREEN + "=== " + ChatColor.GRAY + "Exchange Rates" + ChatColor.GREEN + " ===");
                                            any = true;
                                        }
                                        sendRatesForCurrency(sender, system);
                                    }

                                    if (!any) {
                                        sender.sendMessage(ChatColor.YELLOW + "No exchange rates configured.");
                                    }
                                })
                );
    }

    private Argument<String> currencyArgument(String nodeName) {
        return new StringArgument(nodeName)
                .replaceSuggestions(
                        ArgumentSuggestions.stringCollection(
                                info -> manager.getAll().keySet()
                        )
                );
    }

    private CurrencySystemInstance resolveCurrency(CommandSender sender, String id) {
        CurrencySystemInstance system = manager.get(id);
        if (system == null) {
            sender.sendMessage(ChatColor.RED + "Unknown currency: " + id);
        }
        return system;
    }

    private void sendRatesForCurrency(CommandSender sender, CurrencySystemInstance system) {
        for (Map.Entry<String, Double> entry : system.getExchangeRates().entrySet()) {
            sender.sendMessage(
                    ChatColor.GRAY + "1 " + system.getId() + " = "
                            + CurrencySystemInstance.formatRate(entry.getValue()) + " " + entry.getKey()
            );
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== " + ChatColor.GRAY + "SimpleCurrencies Commands" + ChatColor.GREEN + " ===");
        sender.sendMessage(ChatColor.GRAY + "/sc reload <currency|all>");
        sender.sendMessage(ChatColor.GRAY + "/sc rate <set|get|remove|list>");
        sender.sendMessage(ChatColor.GRAY + "/<currency> give | remove | balance | wipe | transfer");
    }

    private void sendRateHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== " + ChatColor.GRAY + "Exchange Rate Commands" + ChatColor.GREEN + " ===");
        sender.sendMessage(ChatColor.GRAY + "/sc rate set <from> <to> <rate>");
        sender.sendMessage(ChatColor.GRAY + "/sc rate get <from> <to>");
        sender.sendMessage(ChatColor.GRAY + "/sc rate remove <from> <to>");
        sender.sendMessage(ChatColor.GRAY + "/sc rate list [currency]");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.getAll().values().forEach(CurrencySystemInstance::save);
        }

        CommandAPI.onDisable();
        getLogger().info("SimpleCurrencies disabled.");
    }

    public CurrencySystemManager getManager() {
        return manager;
    }
}