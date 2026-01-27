package de.nitrox.SimpleCurrencies;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CurrencySystemInstance {

    private final SimpleCurrencies plugin;
    private final File file;
    private FileConfiguration config;

    private final String id;
    private final String command;
    private final boolean decimals;
    private final String name;

    private List<String> disabledWorlds = new ArrayList<>();

    public CurrencySystemInstance(SimpleCurrencies plugin, String id, String name, File file) {
        this.plugin = plugin;
        this.file = file;
        load();

        this.id = file.getName().replace(".yml", "");
        this.command = config.getString("command", id);
        this.decimals = config.getBoolean("decimals", false);
        this.name = config.getString("name", name);
    }

    public double getBalance(UUID uuid) {
            return config.getDouble("balances." + uuid + ".balance", 0);
    }

    public String getBalanceFormatted(UUID uuid) {
        double balance = getBalance(uuid);
        return String.format(Locale.US, "%.2f %s", balance, name);
    }

    public double getGlobalBalance() {

        ConfigurationSection section =
                config.getConfigurationSection("balances");

        if (section == null) return 0;

        double total = 0;

        for (String key : section.getKeys(false)) {
            total += section.getDouble(key + ".balance", 0);
        }

        if (!decimals) {
            total = Math.floor(total);
        }

        return total;
    }


    public void setBalance(UUID uuid, String name, double value) {
        if (!decimals) {
            value = Math.floor(value);
        }

        String base = "balances." + uuid;
        config.set(base + ".name", name);
        config.set(base + ".balance", value);
        save();
    }

    public void add(UUID uuid, String name, double value) {
        setBalance(uuid, name, getBalance(uuid) + value);
        save();
    }

    public void remove(UUID uuid, String name, double value) {
        setBalance(uuid, name, Math.max(0, getBalance(uuid) - value));
        save();
    }

    public void wipe() {
        config.set("balances", null);
        save();
    }

    public void load() {
        try {
            if (!file.exists()) {
                plugin.saveResource(file.getName(), false);
            }
        } catch (IllegalArgumentException ignore) {
        }

        config = YamlConfiguration.loadConfiguration(file);

        disabledWorlds = config.getStringList("disabled-worlds");
        if (disabledWorlds == null) {
            disabledWorlds = new ArrayList<>();
        }
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save currency system " + id);
            e.printStackTrace();
        }
    }

    public String getId() {
        return id;
    }

    public String getCommand() {
        return command;
    }

    public boolean useDecimals() {
        return decimals;
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void transferTo(CurrencySystemInstance target) {

        ConfigurationSection section =
                config.getConfigurationSection("balances");

        if (section == null) return;

        for (String key : section.getKeys(false)) {
            UUID uuid = UUID.fromString(key);

            String name = section.getString(key + ".name", "Unknown");
            double amount = section.getDouble(key + ".balance", 0);

            target.add(uuid, name, amount);
        }

        wipe();
        save();
        target.save();
    }
}