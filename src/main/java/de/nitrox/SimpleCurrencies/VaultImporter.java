package de.nitrox.SimpleCurrencies;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;
import java.util.*;

public class VaultImporter {

    public static class ImportResult {
        private final boolean success;
        private final String errorMessage;
        private final String economyName;
        private final int playersScanned;
        private final int playersImported;
        private final double totalVaultAmount;
        private final double totalImportedAmount;
        private final double rate;

        private ImportResult(boolean success, String errorMessage, String economyName,
                             int playersScanned, int playersImported,
                             double totalVaultAmount, double totalImportedAmount, double rate) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.economyName = economyName;
            this.playersScanned = playersScanned;
            this.playersImported = playersImported;
            this.totalVaultAmount = totalVaultAmount;
            this.totalImportedAmount = totalImportedAmount;
            this.rate = rate;
        }

        public static ImportResult failure(String errorMessage) {
            return new ImportResult(false, errorMessage, null, 0, 0, 0, 0, 0);
        }

        public static ImportResult success(String economyName, int scanned, int imported,
                                            double vaultAmount, double importedAmount, double rate) {
            return new ImportResult(true, null, economyName, scanned, imported, vaultAmount, importedAmount, rate);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getEconomyName() {
            return economyName;
        }

        public int getPlayersScanned() {
            return playersScanned;
        }

        public int getPlayersImported() {
            return playersImported;
        }

        public double getTotalVaultAmount() {
            return totalVaultAmount;
        }

        public double getTotalImportedAmount() {
            return totalImportedAmount;
        }

        public double getRate() {
            return rate;
        }
    }

    public static ImportResult importVault(SimpleCurrencies plugin, CurrencySystemInstance target, double rate, boolean clearVault) {
        if (target == null) {
            return ImportResult.failure("Target currency cannot be null.");
        }

        if (!CurrencySystemInstance.isValidRate(rate)) {
            return ImportResult.failure("Exchange rate must be greater than 0.");
        }

        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return ImportResult.failure("Vault plugin is not installed on the server.");
        }

        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null || rsp.getProvider() == null) {
            return ImportResult.failure("No Vault economy provider found on the server (e.g. EssentialsX, CMI).");
        }

        Economy economy = rsp.getProvider();
        if (!economy.isEnabled()) {
            return ImportResult.failure("Vault economy provider (" + economy.getName() + ") is disabled.");
        }

        Map<UUID, OfflinePlayer> playerMap = new LinkedHashMap<>();
        Map<UUID, String> fallbackNames = new HashMap<>();

        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            if (p != null && p.getUniqueId() != null) {
                playerMap.put(p.getUniqueId(), p);
                if (p.getName() != null) fallbackNames.put(p.getUniqueId(), p.getName());
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p != null && p.getUniqueId() != null) {
                playerMap.put(p.getUniqueId(), p);
                if (p.getName() != null) fallbackNames.put(p.getUniqueId(), p.getName());
            }
        }

        for (CurrencySystemInstance sys : plugin.getManager().getAll().values()) {
            ConfigurationSection section = sys.getConfig().getConfigurationSection("balances");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    try {
                        UUID u = UUID.fromString(key);
                        if (!playerMap.containsKey(u)) {
                            playerMap.put(u, Bukkit.getOfflinePlayer(u));
                        }
                        String n = section.getString(key + ".name");
                        if (n != null && !fallbackNames.containsKey(u)) {
                            fallbackNames.put(u, n);
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }

        BigDecimal rateDecimal = BigDecimal.valueOf(rate);
        int scanned = 0;
        int imported = 0;
        double totalVaultAmount = 0.0;
        double totalImportedAmount = 0.0;

        for (Map.Entry<UUID, OfflinePlayer> entry : playerMap.entrySet()) {
            UUID uuid = entry.getKey();
            OfflinePlayer player = entry.getValue();
            scanned++;

            String name = player != null ? player.getName() : null;
            if (name == null) {
                name = fallbackNames.get(uuid);
            }
            if (name == null) {
                name = "Unknown";
            }

            double balance = 0.0;
            boolean readSuccess = false;

            if (player != null) {
                try {
                    if (economy.hasAccount(player)) {
                        balance = economy.getBalance(player);
                        readSuccess = true;
                    }
                } catch (Throwable ignored) {}
            }

            if (!readSuccess && name != null && !name.equals("Unknown")) {
                try {
                    if (economy.hasAccount(name)) {
                        balance = economy.getBalance(name);
                        readSuccess = true;
                    }
                } catch (Throwable ignored) {}
            }

            if (!readSuccess && player != null) {
                try {
                    balance = economy.getBalance(player);
                    readSuccess = true;
                } catch (Throwable ignored) {}
            }

            if (!readSuccess || !Double.isFinite(balance) || balance <= 0) {
                continue;
            }

            double converted = BigDecimal.valueOf(balance).multiply(rateDecimal).doubleValue();
            double existingBalance = target.getBalance(uuid);
            double newBalance = BigDecimal.valueOf(existingBalance).add(BigDecimal.valueOf(converted)).doubleValue();

            double maxBalance = target.getMaxBalance(uuid);
            if (maxBalance > 0) {
                newBalance = Math.min(newBalance, maxBalance);
            }

            if (!target.useDecimals()) {
                newBalance = Math.floor(newBalance);
            }

            String base = "balances." + uuid;
            target.getConfig().set(base + ".name", name);
            target.getConfig().set(base + ".balance", newBalance);

            if (clearVault) {
                try {
                    if (player != null) {
                        economy.withdrawPlayer(player, balance);
                    } else if (name != null && !name.equals("Unknown")) {
                        economy.withdrawPlayer(name, balance);
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Could not withdraw " + balance + " from Vault for player " + name + " (" + uuid + "): " + t.getMessage());
                }
            }

            imported++;
            totalVaultAmount += balance;
            totalImportedAmount += converted;
        }

        target.save();

        return ImportResult.success(economy.getName(), scanned, imported,
                totalVaultAmount, totalImportedAmount, rate);
    }
}
