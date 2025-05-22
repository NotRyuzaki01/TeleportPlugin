package me.not_ryuzaki.teleportPlugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPlayedBefore()) {
            Bukkit.getScheduler().runTaskLater(
                    Bukkit.getPluginManager().getPlugin("TeleportPlugin"),
                    () -> {
                        World world = player.getWorld();
                        Location centeredSpawn = world.getSpawnLocation().clone().add(0.5, 0, 0.5);
                        player.teleport(centeredSpawn);
                    },
                    1L
            );
        }
    }
}
