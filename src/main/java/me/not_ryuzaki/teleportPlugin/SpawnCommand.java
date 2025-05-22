package me.not_ryuzaki.teleportPlugin;

import me.not_ryuzaki.mainScorePlugin.Combat;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;

public class SpawnCommand implements CommandExecutor {

    private final TeleportPlugin plugin;
    private final Map<UUID, BukkitRunnable> activeCountdowns;

    public SpawnCommand(TeleportPlugin plugin, Map<UUID, BukkitRunnable> activeCountdowns) {
        this.plugin = plugin;
        this.activeCountdowns = activeCountdowns;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (Combat.isInCombat(player)) {
            player.sendMessage("§cYou can't use /spawn while in combat!");
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cYou're in combat!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }

        if (activeCountdowns.containsKey(player.getUniqueId())) {
            player.sendMessage("§cYou already have a teleport in progress.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }

        Location spawnLoc = player.getWorld().getSpawnLocation().clone().add(0.5, 0, 0.5);
        startTeleportCountdown(player, spawnLoc);
        return true;
    }

    private void startTeleportCountdown(Player teleporting, Location destination) {
        final int[] seconds = {5};
        final Location startLoc = teleporting.getLocation().clone();

        BukkitRunnable old = activeCountdowns.remove(teleporting.getUniqueId());
        if (old != null) old.cancel();

        BukkitRunnable countdown = new BukkitRunnable() {
            @Override
            public void run() {
                if (!teleporting.isOnline()) {
                    Combat.unregisterTeleportCallback(teleporting.getUniqueId());
                    activeCountdowns.remove(teleporting.getUniqueId());
                    cancel();
                    return;
                }

                if (Combat.isInCombat(teleporting)) {
                    teleporting.sendMessage("§cTeleport cancelled — you entered combat!");
                    teleporting.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cTeleport cancelled — in combat!"));
                    teleporting.playSound(teleporting.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    Combat.unregisterTeleportCallback(teleporting.getUniqueId());
                    activeCountdowns.remove(teleporting.getUniqueId());
                    cancel();
                    return;
                }

                Location currentLoc = teleporting.getLocation();
                if (currentLoc.distanceSquared(startLoc) > 0.01) {
                    teleporting.sendMessage("§cTeleport cancelled because you moved!");
                    teleporting.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cTeleport cancelled because you moved!"));
                    teleporting.playSound(currentLoc, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    Combat.unregisterTeleportCallback(teleporting.getUniqueId());
                    activeCountdowns.remove(teleporting.getUniqueId());
                    cancel();
                    return;
                }

                if (seconds[0] == 0) {
                    teleporting.teleport(destination);
                    teleporting.sendMessage("§aTeleported to spawn!");
                    teleporting.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§aTeleported to spawn!"));
                    teleporting.playSound(teleporting.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    Combat.unregisterTeleportCallback(teleporting.getUniqueId());
                    activeCountdowns.remove(teleporting.getUniqueId());
                    cancel();
                    return;
                }

                TextComponent message = new TextComponent("Teleporting in ");
                message.setColor(ChatColor.WHITE);
                TextComponent countdownNumber = new TextComponent(String.valueOf(seconds[0]));
                countdownNumber.setColor(ChatColor.of("#0094FF"));
                TextComponent suffix = new TextComponent("s");
                suffix.setColor(ChatColor.of("#0094FF"));

                message.addExtra(countdownNumber);
                message.addExtra(suffix);

                teleporting.spigot().sendMessage(ChatMessageType.ACTION_BAR, message);
                teleporting.playSound(teleporting.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);

                seconds[0]--;
            }
        };

        countdown.runTaskTimer(plugin, 0L, 20L);
        activeCountdowns.put(teleporting.getUniqueId(), countdown);

        // Register cancel callback on combat entry
        Combat.registerTeleportCancelCallback(teleporting.getUniqueId(), () -> {
            countdown.cancel();
            teleporting.sendMessage("§cTeleport cancelled — you entered combat!");
            teleporting.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cTeleport cancelled — in combat!"));
            teleporting.playSound(teleporting.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            activeCountdowns.remove(teleporting.getUniqueId());
        });
    }
}
