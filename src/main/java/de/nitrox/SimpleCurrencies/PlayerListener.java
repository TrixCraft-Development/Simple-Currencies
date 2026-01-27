package de.nitrox.SimpleCurrencies;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

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
}