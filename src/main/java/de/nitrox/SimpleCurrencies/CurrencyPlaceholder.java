package de.nitrox.SimpleCurrencies;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.UUID;

public class CurrencyPlaceholder extends PlaceholderExpansion {

    private final SimpleCurrencies plugin;

    public CurrencyPlaceholder(SimpleCurrencies plugin) {
        this.plugin = plugin;
    }

    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getIdentifier() {
        return "simplecurrencies";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || params == null || params.isEmpty()) return "";

        if (params.endsWith("_global_balance")) {
            String systemid = params.substring(0, params.length() - "_global_balance".length());
            return handleGlobalBalance(systemid, player);
        }
        if (params.endsWith("_balance_formatted")) {
            String systemid = params.substring(0, params.length() - "_balance_formatted".length());
            return handleBalanceFormatted(systemid, player);
        }
        if (params.endsWith("_balance")) {
            String systemid = params.substring(0, params.length() - "_balance".length());
            return handleBalance(systemid, player);
        }
        if (params.endsWith("_max_balance")) {
            String systemid = params.substring(0, params.length() - "_max_balance".length());
            return handleMaxBalance(systemid);
        }
        return null;
    }

    private String handleBalance(String systemid, Player player) {
        CurrencySystemInstance inst = plugin.getManager().get(systemid);
        if (inst == null) return null;
        double balance = inst.getBalance(player.getUniqueId());
        return String.valueOf(balance);
    }

    private String handleBalanceFormatted(String systemid, Player player) {
        CurrencySystemInstance inst = plugin.getManager().get(systemid);
        if (inst == null) return null;
        String balanceFormatted = inst.getBalanceFormatted(player.getUniqueId());
        return String.valueOf(balanceFormatted);
    }

    private String handleGlobalBalance(String systemid, Player player) {
        CurrencySystemInstance inst = plugin.getManager().get(systemid);
        if (inst == null) return null;
        double globalbalance = inst.getGlobalBalance();
        return String.valueOf(globalbalance);

    }

    private String handleMaxBalance(String systemid) {
        CurrencySystemInstance inst = plugin.getManager().get(systemid);
        if (inst == null) return null;
        double maxBalance = inst.getDefaultMaxBalance();
        return String.valueOf(maxBalance);
    }
}