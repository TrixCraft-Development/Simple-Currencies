package de.nitrox.SimpleCurrencies;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

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

    private static final String PDC_CURRENCY_KEY = "currency_id";
    private static final String PDC_AMOUNT_KEY = "withdraw_amount";

    private boolean payEnabled = true;
    private boolean withdrawEnabled = false;
    private Material withdrawMaterial = Material.PAPER;
    private String withdrawDisplayName = "&a%amount% %currency%";
    private List<String> withdrawLore = new ArrayList<>();
    private List<String> disabledWorlds = new ArrayList<>();
    private final Map<String, Double> exchangeRates = new LinkedHashMap<>();

    private boolean autoImportEnabled = false;
    private String autoImportSource = "Vault";
    private long autoImportIntervalSeconds = 60L;
    private Double autoImportRate = null;
    private boolean autoImportClearSource = true;
    private boolean autoImportLogConsole = true;

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

        if (config.contains("withdraw.enabled")) {
            this.withdrawEnabled = config.getBoolean("withdraw.enabled", false);
        } else if (config.contains("withdraw-enabled")) {
            this.withdrawEnabled = config.getBoolean("withdraw-enabled", false);
        } else {
            this.withdrawEnabled = false;
        }

        String rawMaterial = config.getString(
                "withdraw.item",
                config.getString("withdraw.material", config.getString("withdraw-item", "PAPER"))
        );
        Material matched = rawMaterial != null ? Material.matchMaterial(rawMaterial.trim()) : null;
        if (matched == null || matched.isAir() || !matched.isItem()) {
            plugin.getLogger().warning(
                    "Invalid withdraw item '" + rawMaterial + "' in " + id + ".yml; falling back to PAPER."
            );
            this.withdrawMaterial = Material.PAPER;
        } else {
            this.withdrawMaterial = matched;
        }

        this.withdrawDisplayName = config.getString(
                "withdraw.display-name",
                config.getString("withdraw.name", config.getString("withdraw-display-name", "&a%amount% %currency%"))
        );

        if (config.contains("withdraw.lore")) {
            this.withdrawLore = new ArrayList<>(config.getStringList("withdraw.lore"));
        } else if (config.contains("withdraw-lore")) {
            this.withdrawLore = new ArrayList<>(config.getStringList("withdraw-lore"));
        } else {
            this.withdrawLore = new ArrayList<>(List.of(
                    "&7Value: &e%amount% %currency%",
                    "&7Withdrawn by: &f%player%",
                    "&8Right-click to redeem"
            ));
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

        loadAutoImportSettings();
    }

    private void loadAutoImportSettings() {
        ConfigurationSection section = config.getConfigurationSection("auto-import");
        if (section == null) {
            section = config.getConfigurationSection("vault-auto-import");
        }
        if (section == null) {
            section = config.getConfigurationSection("vault-import");
        }

        if (section == null) {
            this.autoImportEnabled = config.getBoolean("auto-import-enabled", false);
            this.autoImportSource = "Vault";
            this.autoImportIntervalSeconds = 60L;
            this.autoImportRate = null;
            this.autoImportClearSource = true;
            this.autoImportLogConsole = true;
            return;
        }

        this.autoImportEnabled = section.getBoolean("enabled", false);

        String configuredSource = section.getString("source", "Vault");
        this.autoImportSource = (configuredSource == null || configuredSource.isBlank())
                ? "Vault"
                : configuredSource.trim();

        Object rawInterval = section.get("interval", section.get("interval-seconds", 60));
        this.autoImportIntervalSeconds = parseIntervalSeconds(rawInterval, 60L);

        if (section.contains("rate")) {
            double r = section.getDouble("rate", -1);
            if (isValidRate(r)) {
                this.autoImportRate = r;
            } else {
                plugin.getLogger().warning(
                        "Invalid auto-import rate in " + id + ".yml: " + r + "; falling back to default rate."
                );
                this.autoImportRate = null;
            }
        } else {
            this.autoImportRate = null;
        }

        if (section.contains("clear-source")) {
            this.autoImportClearSource = section.getBoolean("clear-source", true);
        } else if (section.contains("clear-vault")) {
            this.autoImportClearSource = section.getBoolean("clear-vault", true);
        } else if (section.contains("delete-vault")) {
            this.autoImportClearSource = section.getBoolean("delete-vault", true);
        } else if (section.contains("delete-source")) {
            this.autoImportClearSource = section.getBoolean("delete-source", true);
        } else {
            this.autoImportClearSource = true;
        }

        if (section.contains("log-console")) {
            this.autoImportLogConsole = section.getBoolean("log-console", true);
        } else if (section.contains("log")) {
            this.autoImportLogConsole = section.getBoolean("log", true);
        } else {
            this.autoImportLogConsole = true;
        }
    }

    public static long parseIntervalSeconds(Object rawInterval, long defaultSeconds) {
        if (rawInterval == null) return defaultSeconds;
        if (rawInterval instanceof Number num) {
            long val = num.longValue();
            return val > 0 ? val : defaultSeconds;
        }

        String text = rawInterval.toString().trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) return defaultSeconds;

        long multiplier = 1L;
        String numberPart = text;

        if (text.endsWith("seconds") || text.endsWith("second")) {
            numberPart = text.replaceAll("(seconds|second)$", "").trim();
        } else if (text.endsWith("secs") || text.endsWith("sec")) {
            numberPart = text.replaceAll("(secs|sec)$", "").trim();
        } else if (text.endsWith("s")) {
            numberPart = text.substring(0, text.length() - 1).trim();
        } else if (text.endsWith("minutes") || text.endsWith("minute")) {
            multiplier = 60L;
            numberPart = text.replaceAll("(minutes|minute)$", "").trim();
        } else if (text.endsWith("mins") || text.endsWith("min")) {
            multiplier = 60L;
            numberPart = text.replaceAll("(mins|min)$", "").trim();
        } else if (text.endsWith("m")) {
            multiplier = 60L;
            numberPart = text.substring(0, text.length() - 1).trim();
        } else if (text.endsWith("hours") || text.endsWith("hour")) {
            multiplier = 3600L;
            numberPart = text.replaceAll("(hours|hour)$", "").trim();
        } else if (text.endsWith("hrs") || text.endsWith("hr")) {
            multiplier = 3600L;
            numberPart = text.replaceAll("(hrs|hr)$", "").trim();
        } else if (text.endsWith("h")) {
            multiplier = 3600L;
            numberPart = text.substring(0, text.length() - 1).trim();
        }

        try {
            double parsed = Double.parseDouble(numberPart);
            if (!Double.isFinite(parsed) || parsed <= 0) return defaultSeconds;
            long seconds = Math.round(parsed * multiplier);
            return Math.max(1L, seconds);
        } catch (NumberFormatException e) {
            return defaultSeconds;
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

    public boolean isWithdrawEnabled() {
        return withdrawEnabled;
    }

    public void setWithdrawEnabled(boolean withdrawEnabled) {
        this.withdrawEnabled = withdrawEnabled;
        config.set("withdraw.enabled", withdrawEnabled);
        save();
    }

    public Material getWithdrawMaterial() {
        return withdrawMaterial;
    }

    public String getWithdrawDisplayName() {
        return withdrawDisplayName;
    }

    public List<String> getWithdrawLore() {
        return Collections.unmodifiableList(withdrawLore);
    }

    public static NamespacedKey getCurrencyKey(SimpleCurrencies plugin) {
        return new NamespacedKey(plugin, PDC_CURRENCY_KEY);
    }

    public static NamespacedKey getAmountKey(SimpleCurrencies plugin) {
        return new NamespacedKey(plugin, PDC_AMOUNT_KEY);
    }

    public static String extractWithdrawCurrencyId(SimpleCurrencies plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(getCurrencyKey(plugin), PersistentDataType.STRING);
    }

    public static Double extractWithdrawAmount(SimpleCurrencies plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(getAmountKey(plugin), PersistentDataType.DOUBLE);
    }

    public ItemStack createWithdrawItem(double amount, String playerName) {
        ItemStack item = new ItemStack(withdrawMaterial, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (withdrawDisplayName != null && !withdrawDisplayName.isEmpty()) {
                meta.setDisplayName(formatWithdrawText(withdrawDisplayName, amount, playerName));
            }

            if (withdrawLore != null && !withdrawLore.isEmpty()) {
                List<String> formattedLore = new ArrayList<>(withdrawLore.size());
                for (String line : withdrawLore) {
                    formattedLore.add(formatWithdrawText(line, amount, playerName));
                }
                meta.setLore(formattedLore);
            }

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(getCurrencyKey(plugin), PersistentDataType.STRING, id.toLowerCase(Locale.ROOT));
            pdc.set(getAmountKey(plugin), PersistentDataType.DOUBLE, amount);

            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatWithdrawText(String template, double amount, String playerName) {
        String formattedAmount = formatNumber(amount);
        String safePlayer = playerName != null ? playerName : "Unknown";
        String replaced = template
                .replace("%amount%", formattedAmount)
                .replace("{amount}", formattedAmount)
                .replace("%currency%", name)
                .replace("{currency}", name)
                .replace("%currency_id%", id)
                .replace("{currency_id}", id)
                .replace("%player%", safePlayer)
                .replace("{player}", safePlayer);
        return ChatColor.translateAlternateColorCodes('&', replaced);
    }

    public boolean withdraw(Player player, double amount) {
        if (!withdrawEnabled) {
            player.sendMessage(ChatColor.RED + "Withdrawing is disabled for this currency.");
            return false;
        }

        if (!Double.isFinite(amount) || amount <= 0) {
            player.sendMessage(ChatColor.RED + "The amount must be greater than 0.");
            return false;
        }

        if (!decimals && amount != Math.floor(amount)) {
            player.sendMessage(ChatColor.RED + "This currency does not support decimal amounts.");
            return false;
        }

        double currentBalance = getBalance(player.getUniqueId());
        if (currentBalance < amount) {
            player.sendMessage(ChatColor.RED + "You do not have enough " + name + ".");
            return false;
        }

        double newBalance = BigDecimal.valueOf(currentBalance)
                .subtract(BigDecimal.valueOf(amount))
                .doubleValue();
        if (!decimals) {
            newBalance = Math.floor(newBalance);
        }

        String base = "balances." + player.getUniqueId();
        config.set(base + ".name", player.getName());
        config.set(base + ".balance", Math.max(0, newBalance));
        save();

        ItemStack item = createWithdrawItem(amount, player.getName());
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            player.sendMessage(
                    ChatColor.YELLOW + "Your inventory is full; the withdrawn item was dropped at your feet."
            );
        }

        player.sendMessage(
                ChatColor.GREEN + "Withdrew " + formatNumber(amount) + " " + name + "."
        );
        return true;
    }

    public boolean depositHeldItem(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (extractWithdrawCurrencyId(plugin, mainHand) != null) {
            return redeemWithdrawItem(player, mainHand, true);
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (extractWithdrawCurrencyId(plugin, offHand) != null) {
            return redeemWithdrawItem(player, offHand, true);
        }

        player.sendMessage(ChatColor.RED + "You must hold a withdrawn " + name + " item in your hand to deposit it.");
        return false;
    }

    public boolean redeemWithdrawItem(Player player, ItemStack item, boolean redeemFullStack) {
        String itemCurrencyId = extractWithdrawCurrencyId(plugin, item);
        Double unitAmount = extractWithdrawAmount(plugin, item);

        if (itemCurrencyId == null || unitAmount == null || !Double.isFinite(unitAmount) || unitAmount <= 0) {
            player.sendMessage(ChatColor.RED + "You are not holding a valid currency item.");
            return false;
        }

        if (!itemCurrencyId.equalsIgnoreCase(this.id)) {
            player.sendMessage(
                    ChatColor.RED + "This item belongs to currency '" + itemCurrencyId + "', not '" + this.id + "'."
            );
            return false;
        }

        int stackAmount = item.getAmount();
        if (stackAmount <= 0) return false;

        int maxCountToRedeem = redeemFullStack ? stackAmount : 1;
        double currentBalance = getBalance(player.getUniqueId());
        double maxBalance = getMaxBalance(player.getUniqueId());

        int countToRedeem = 0;
        BigDecimal runningBalance = BigDecimal.valueOf(currentBalance);
        BigDecimal unitDecimal = BigDecimal.valueOf(unitAmount);

        for (int i = 0; i < maxCountToRedeem; i++) {
            BigDecimal nextBalance = runningBalance.add(unitDecimal);
            if (maxBalance > 0 && nextBalance.doubleValue() > maxBalance) {
                break;
            }
            runningBalance = nextBalance;
            countToRedeem++;
        }

        if (countToRedeem == 0) {
            player.sendMessage(
                    ChatColor.RED + "You cannot redeem this item because it would exceed your maximum balance."
            );
            return false;
        }

        double totalDeposited = unitDecimal.multiply(BigDecimal.valueOf(countToRedeem)).doubleValue();
        double finalBalance = runningBalance.doubleValue();
        if (!decimals) {
            finalBalance = Math.floor(finalBalance);
        }

        item.setAmount(stackAmount - countToRedeem);

        String base = "balances." + player.getUniqueId();
        config.set(base + ".name", player.getName());
        config.set(base + ".balance", finalBalance);
        save();

        player.sendMessage(
                ChatColor.GREEN + "Deposited " + formatNumber(totalDeposited) + " " + name + " into your account."
        );
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

    public boolean isAutoImportEnabled() {
        return autoImportEnabled;
    }

    public String getAutoImportSource() {
        return autoImportSource;
    }

    public long getAutoImportIntervalSeconds() {
        return autoImportIntervalSeconds;
    }

    public double getEffectiveAutoImportRate() {
        if (autoImportRate != null && isValidRate(autoImportRate)) {
            return autoImportRate;
        }
        Double configuredOnTarget = getConfiguredExchangeRate(autoImportSource);
        if (configuredOnTarget != null && isValidRate(configuredOnTarget)) {
            return configuredOnTarget;
        }
        if (plugin.getManager() != null) {
            CurrencySystemInstance sourceSystem = plugin.getManager().get(autoImportSource);
            if (sourceSystem != null) {
                Double configuredOnSource = sourceSystem.getConfiguredExchangeRate(this.id);
                if (configuredOnSource != null && isValidRate(configuredOnSource)) {
                    return configuredOnSource;
                }
            }
        }
        return 1.0;
    }

    public boolean isAutoImportClearSource() {
        return autoImportClearSource;
    }

    public boolean isAutoImportLogConsole() {
        return autoImportLogConsole;
    }

    public void runAutoImport() {
        if (!autoImportEnabled) {
            return;
        }

        double rate = getEffectiveAutoImportRate();

        if (autoImportSource.equalsIgnoreCase("vault")) {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                return;
            }

            VaultImporter.ImportResult result = importFromVault(rate, autoImportClearSource);
            if (result.isSuccess() && result.getPlayersImported() > 0 && autoImportLogConsole) {
                plugin.getLogger().info(
                        "[Auto-Import] Transferred " + result.getPlayersImported()
                                + " player(s) from Vault (" + result.getEconomyName() + ") into " + id
                                + " (rate: 1 Vault = " + formatRate(rate) + " " + id
                                + ", total added: " + formatNumber(result.getTotalImportedAmount()) + " " + name
                                + (autoImportClearSource ? ", Vault cleared" : "") + ")."
                );
            }
            return;
        }

        if (plugin.getManager() == null) {
            return;
        }

        CurrencySystemInstance sourceSystem = plugin.getManager().get(autoImportSource);
        if (sourceSystem == null || sourceSystem.getId().equalsIgnoreCase(this.id)) {
            return;
        }

        ConfigurationSection section = sourceSystem.getConfig().getConfigurationSection("balances");
        if (section == null) {
            return;
        }

        BigDecimal rateDecimal = BigDecimal.valueOf(rate);
        int transferredPlayers = 0;
        double totalAdded = 0.0;

        for (String key : new ArrayList<>(section.getKeys(false))) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }

            double amount = section.getDouble(key + ".balance", 0);
            if (!Double.isFinite(amount) || amount <= 0) {
                continue;
            }

            String playerName = section.getString(key + ".name", "Unknown");
            double converted = BigDecimal.valueOf(amount).multiply(rateDecimal).doubleValue();
            double existing = getBalance(uuid);
            double newBalance = BigDecimal.valueOf(existing).add(BigDecimal.valueOf(converted)).doubleValue();

            double maxBalance = getMaxBalance(uuid);
            if (maxBalance > 0) {
                newBalance = Math.min(newBalance, maxBalance);
            }
            if (!decimals) {
                newBalance = Math.floor(newBalance);
            }

            String base = "balances." + uuid;
            config.set(base + ".name", playerName);
            config.set(base + ".balance", newBalance);

            if (autoImportClearSource) {
                sourceSystem.getConfig().set("balances." + key + ".balance", 0);
            }

            transferredPlayers++;
            totalAdded += converted;
        }

        if (transferredPlayers > 0) {
            save();
            if (autoImportClearSource) {
                sourceSystem.save();
            }

            if (autoImportLogConsole) {
                plugin.getLogger().info(
                        "[Auto-Import] Transferred " + transferredPlayers
                                + " player(s) from " + sourceSystem.getId() + " into " + id
                                + " (rate: 1 " + sourceSystem.getId() + " = " + formatRate(rate) + " " + id
                                + ", total added: " + formatNumber(totalAdded) + " " + name
                                + (autoImportClearSource ? ", source cleared" : "") + ")."
                );
            }
        }
    }
}
