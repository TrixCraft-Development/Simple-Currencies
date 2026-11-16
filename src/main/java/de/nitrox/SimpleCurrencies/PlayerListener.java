package de.nitrox.SimpleCurrencies;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerListener implements Listener {

    private final SimpleCurrencies plugin;

    public PlayerListener(SimpleCurrencies plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        plugin.getManager().getAll().values().forEach(system -> {
            String base = "balances." + p.getUniqueId();

            if (!system.getConfig().contains(base)) {
                double start = system.getConfig().getDouble("start-balance", 0);
                system.setBalance(p.getUniqueId(), p.getName(), start);
                system.save();
            } else {
                system.getConfig().set(base + ".name", p.getName());
            }
        });
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = e.getItem();
        String currencyId = CurrencySystemInstance.extractWithdrawCurrencyId(plugin, item);
        if (currencyId == null) {
            return;
        }

        e.setCancelled(true);
        e.setUseItemInHand(Event.Result.DENY);
        e.setUseInteractedBlock(Event.Result.DENY);

        Player player = e.getPlayer();
        CurrencySystemInstance system = plugin.getManager().get(currencyId);
        if (system == null) {
            player.sendMessage(ChatColor.RED + "The currency '" + currencyId + "' no longer exists.");
            return;
        }

        system.redeemWithdrawItem(player, item, false);
    }
}
