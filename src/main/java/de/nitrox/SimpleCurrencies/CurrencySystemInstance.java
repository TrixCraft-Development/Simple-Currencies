package de.nitrox.SimpleCurrencies;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

public class CurrencySystemInstance {

    private final SimpleCurrencies plugin;
    private final File file;
    private FileConfiguration config;

    private final String id;
    private final String command;
    private final boolean decimals;
    private final String name;

    private boolean payEnabled = true;
    private List<String> disabledWorlds = new ArrayList<>();
    private final Map<String, Double> exchangeRates = new LinkedHashMap<>();

    public CurrencySystemInstance(SimpleCurrencies plugin, String id, String name, File file) {
        this.plugin = plugin;
        this.file = file;
        this.id = file.getName().replace(".yml", "");
        load();

        this.command = config.getString("command", id);
        this.decimals = config.getBoolean("decimals", false);
        this.name = config.getString("name", name);
    }

    public double getBalance(UUID uuid) {
            return config.getDouble("balances." + uuid + ".balance", 0);
    }

    public String getBalanceFormatted(UUID uuid) {
        double balance = getBalance(uuid);
        return formatNumber(balance) + " " + name;
    }

    public String getBalanceString(UUID uuid) {
        return formatNumber(getBalance(uuid));
    }

    public String getGlobalBalanceString() {
        return formatNumber(getGlobalBalance());
    }

    public String getDefaultMaxBalanceString() {
        return formatNumber(getDefaultMaxBalance());
    }

    public String formatNumber(double value) {
        if (!decimals) {
            return String.valueOf((long) Math.floor(value));
        }
        return String.valueOf(value);
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
        double maxBalance = getMaxBalance(uuid);
        if (maxBalance > 0) {
            value = Math.min(value, maxBalance);
        }

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

    public double getMaxBalance(UUID uuid) {
        double max = config.getDouble("max-balance", 0);

        ConfigurationSection section = config.getConfigurationSection("max-balance-permissions");
        if (section == null) {
            return max;
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return max;
        }

        for (String permission : section.getKeys(false)) {
            double value = section.getDouble(permission, max);
            if (player.hasPermission(permission) && value > max) {
                max = value;
            }
        }

        return max;
    }

    public double getDefaultMaxBalance() {
        return config.getDouble("max-balance", 0);
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

        if (config.contains("pay-enabled")) {
            this.payEnabled = config.getBoolean("pay-enabled", true);
        } else if (config.contains("payments-enabled")) {
            this.payEnabled = config.getBoolean("payments-enabled", true);
        } else if (config.contains("payment-enabled")) {
            this.payEnabled = config.getBoolean("payment-enabled", true);
        } else {
            this.payEnabled = true;
        }

        disabledWorlds = config.getStringList("disabled-worlds");
        if (disabledWorlds == null) {
            disabledWorlds = new ArrayList<>();
        }

        exchangeRates.clear();
        ConfigurationSection ratesSection = config.getConfigurationSection("exchange-rates");
        if (ratesSection != null) {
            for (String targetKey : ratesSection.getKeys(false)) {
                double rate = ratesSection.getDouble(targetKey, -1);
                if (!isValidRate(rate)) {
                    plugin.getLogger().warning(
                            "Invalid exchange rate in " + id + ".yml for target '" + targetKey + "': " + rate
                    );
                    continue;
                }
                exchangeRates.put(targetKey.toLowerCase(Locale.ROOT), rate);
            }
        }
    }

    public static boolean isValidRate(double rate) {
        return Double.isFinite(rate) && rate > 0;
    }

    public static String formatRate(double rate) {
        return BigDecimal.valueOf(rate).stripTrailingZeros().toPlainString();
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

    public String getName() {
        return name;
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

    public boolean isPayEnabled() {
        return payEnabled;
    }

    public boolean isPaymentsEnabled() {
        return isPayEnabled();
    }

    public void setPayEnabled(boolean payEnabled) {
        this.payEnabled = payEnabled;
        config.set("pay-enabled", payEnabled);
        save();
    }

    public boolean pay(Player sender, Player target, double amount) {
        if (!payEnabled) {
            sender.sendMessage(ChatColor.RED + "Payments are disabled for this currency.");
            return false;
        }

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return false;
        }

        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "You cannot pay yourself.");
            return false;
        }

        if (!Double.isFinite(amount) || amount <= 0) {
            sender.sendMessage(ChatColor.RED + "The amount must be greater than 0.");
            return false;
        }

        if (!decimals && amount != Math.floor(amount)) {
            sender.sendMessage(ChatColor.RED + "This currency does not support decimal amounts.");
            return false;
        }

        double senderBalance = getBalance(sender.getUniqueId());
        if (senderBalance < amount) {
            sender.sendMessage(ChatColor.RED + "You do not have enough " + name + ".");
            return false;
        }

        double targetMax = getMaxBalance(target.getUniqueId());
        double targetBalance = getBalance(target.getUniqueId());
        if (targetMax > 0 && targetBalance + amount > targetMax) {
            sender.sendMessage(ChatColor.RED + target.getName() + " cannot receive this amount (exceeds maximum balance).");
            return false;
        }

        double newSenderBalance = BigDecimal.valueOf(senderBalance)
                .subtract(BigDecimal.valueOf(amount))
                .doubleValue();
        double newTargetBalance = BigDecimal.valueOf(targetBalance)
                .add(BigDecimal.valueOf(amount))
                .doubleValue();

        if (!decimals) {
            newSenderBalance = Math.floor(newSenderBalance);
            newTargetBalance = Math.floor(newTargetBalance);
        }

        String senderBase = "balances." + sender.getUniqueId();
        config.set(senderBase + ".name", sender.getName());
        config.set(senderBase + ".balance", newSenderBalance);

        String targetBase = "balances." + target.getUniqueId();
        config.set(targetBase + ".name", target.getName());
        config.set(targetBase + ".balance", newTargetBalance);

        save();

        sender.sendMessage(ChatColor.GREEN + "You paid " + target.getName() + " " + formatNumber(amount) + " " + name + ".");
        target.sendMessage(ChatColor.GREEN + "You received " + formatNumber(amount) + " " + name + " from " + sender.getName() + ".");
        return true;
    }

    public boolean hasExchangeRate(String targetId) {
        if (targetId == null) return false;
        return exchangeRates.containsKey(targetId.toLowerCase(Locale.ROOT));
    }

    public Double getConfiguredExchangeRate(String targetId) {
        if (targetId == null) return null;
        return exchangeRates.get(targetId.toLowerCase(Locale.ROOT));
    }

    public double getExchangeRate(String targetId) {
        Double rate = getConfiguredExchangeRate(targetId);
        return rate != null ? rate : 1.0;
    }

    public Map<String, Double> getExchangeRates() {
        return Collections.unmodifiableMap(exchangeRates);
    }

    public void setExchangeRate(String targetId, double rate) {
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("Target currency id must not be blank.");
        }
        if (!isValidRate(rate)) {
            throw new IllegalArgumentException("Exchange rate must be greater than 0.");
        }

        clearConfiguredRateKeys(targetId);
        config.set("exchange-rates." + targetId, rate);
        exchangeRates.put(targetId.toLowerCase(Locale.ROOT), rate);
        save();
    }

    public boolean removeExchangeRate(String targetId) {
        if (targetId == null || !hasExchangeRate(targetId)) {
            return false;
        }

        clearConfiguredRateKeys(targetId);
        exchangeRates.remove(targetId.toLowerCase(Locale.ROOT));
        save();
        return true;
    }

    private void clearConfiguredRateKeys(String targetId) {
        ConfigurationSection ratesSection = config.getConfigurationSection("exchange-rates");
        if (ratesSection == null) return;

        for (String existingKey : new ArrayList<>(ratesSection.getKeys(false))) {
            if (existingKey.equalsIgnoreCase(targetId)) {
                config.set("exchange-rates." + existingKey, null);
            }
        }
    }

    public void transferTo(CurrencySystemInstance target) {
        transferTo(target, getExchangeRate(target.getId()));
    }

    public void transferTo(CurrencySystemInstance target, double rate) {
        if (target == null) {
            throw new IllegalArgumentException("Target currency must not be null.");
        }
        if (target.getId().equalsIgnoreCase(this.id)) {
            throw new IllegalArgumentException("Cannot transfer a currency to itself.");
        }
        if (!isValidRate(rate)) {
            throw new IllegalArgumentException("Exchange rate must be greater than 0.");
        }

        ConfigurationSection section =
                config.getConfigurationSection("balances");

        if (section == null) return;

        BigDecimal rateDecimal = BigDecimal.valueOf(rate);

        for (String key : section.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping invalid UUID in " + id + ".yml during transfer: " + key);
                continue;
            }

            String playerName = section.getString(key + ".name", "Unknown");
            double amount = section.getDouble(key + ".balance", 0);
            double converted = BigDecimal.valueOf(amount).multiply(rateDecimal).doubleValue();

            target.add(uuid, playerName, converted);
        }

        wipe();
        save();
        target.save();
    }

    public VaultImporter.ImportResult importFromVault(double rate, boolean clearVault) {
        return VaultImporter.importVault(plugin, this, rate, clearVault);
    }
}
