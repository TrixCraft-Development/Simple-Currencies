package de.nitrox.SimpleCurrencies;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIPaperConfig;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.StringArgument;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

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
                .executes((sender, args) -> {
                    sender.sendMessage(ChatColor.GREEN + "=== " + ChatColor.GRAY +"SimpleCurrencies Commands" + ChatColor.GREEN + " ===");
                    sender.sendMessage(ChatColor.GRAY + "/sc reload <currency | all>");
                    sender.sendMessage(ChatColor.GRAY + "/<currency> give | remove | balance | wipe | transfer");
                })

                // /sc reload <currency|all>
                .withSubcommand(
                        new CommandAPICommand("reload")
                                .withPermission("simplecurrencies.reload")
                                .withArguments(
                                        new StringArgument("currency")
                                                .replaceSuggestions(
                                                        ArgumentSuggestions.stringCollection(
                                                                info -> manager.getAll().keySet()
                                                        )
                                                )
                                )
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

                // /sc help
                .withSubcommand(
                        new CommandAPICommand("help")
                                .executes((sender, args) -> {
                                    sender.sendMessage(ChatColor.GREEN + "=== " + ChatColor.GRAY +"SimpleCurrencies Commands" + ChatColor.GREEN + " ===");
                                    sender.sendMessage(ChatColor.GRAY + "/sc reload <currency|all>");
                                    sender.sendMessage(ChatColor.GRAY + "/<currency> give | remove | balance | wipe | transfer");
                                })
                )

                .register();
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