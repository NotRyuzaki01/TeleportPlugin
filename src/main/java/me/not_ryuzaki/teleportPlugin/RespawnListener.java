package me.not_ryuzaki.teleportPlugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public class RespawnListener implements Listener {

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        World world = event.getPlayer().getWorld();
        Location worldSpawn = world.getSpawnLocation().clone().add(0.5, 0, 0.5);

        boolean isWorldSpawn = !event.isBedSpawn() && !event.isAnchorSpawn();

        if (isWorldSpawn) {
            // Forcefully teleport 1 tick later to override vanilla safe-spawn behavior
            Bukkit.getScheduler().runTaskLater(
                    Bukkit.getPluginManager().getPlugin("TeleportPlugin"),
                    () -> event.getPlayer().teleport(worldSpawn),
                    1L
            );
        } else {
            // Even for bed/anchor, center the block
            Location centered = event.getRespawnLocation().clone().add(0.5, 0, 0.5);
            event.setRespawnLocation(centered);
        }
    }
}
