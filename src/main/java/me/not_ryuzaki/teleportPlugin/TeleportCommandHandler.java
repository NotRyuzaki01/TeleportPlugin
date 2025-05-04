package me.not_ryuzaki.teleportPlugin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class TeleportCommandHandler implements CommandExecutor, TabCompleter {
    private final TeleportPlugin plugin;
    private final Map<UUID, TeleportRequest> requests = new HashMap<>();
    private final Map<UUID, BukkitRunnable> activeCountdowns = new HashMap<>();

    public TeleportCommandHandler(TeleportPlugin plugin) {
        this.plugin = plugin;
    }

    private static class TeleportRequest {
        final UUID requester;
        final boolean here;

        TeleportRequest(UUID requester, boolean here) {
            this.requester = requester;
            this.here = here;
        }
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    private void startTeleportCountdown(Player teleporting, Player target) {
        final int[] seconds = {5};
        final Location startLoc = teleporting.getLocation().clone();

        // Cancel any existing countdown
        BukkitRunnable old = activeCountdowns.remove(teleporting.getUniqueId());
        if (old != null) old.cancel();

        BukkitRunnable countdown = new BukkitRunnable() {
            @Override
            public void run() {
                if (!teleporting.isOnline() || !target.isOnline()) {
                    activeCountdowns.remove(teleporting.getUniqueId());
                    cancel();
                    return;
                }

                Location currentLoc = teleporting.getLocation();
                if (currentLoc.getX() != startLoc.getX() ||
                        currentLoc.getY() != startLoc.getY() ||
                        currentLoc.getZ() != startLoc.getZ()) {

                    teleporting.sendMessage("§cTeleport cancelled because you moved!");
                    teleporting.playSound(currentLoc, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    target.sendMessage("§eTeleport cancelled because " + teleporting.getName() + " moved.");
                    target.playSound(target.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    activeCountdowns.remove(teleporting.getUniqueId());
                    cancel();
                    return;
                }

                if (seconds[0] <= 0) {
                    teleporting.teleport(target.getLocation());
                    teleporting.sendMessage("§aTeleported!");
                    target.sendMessage("§aTeleport completed.");
                    teleporting.playSound(teleporting.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    activeCountdowns.remove(teleporting.getUniqueId());
                    cancel();
                    return;
                }

                String msg = "§eTeleporting in " + seconds[0] + "...";
                sendActionBar(teleporting, msg);
                sendActionBar(target, msg);
                teleporting.playSound(teleporting.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                target.playSound(target.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                seconds[0]--;
            }
        };

        countdown.runTaskTimer(plugin, 0L, 20L);
        activeCountdowns.put(teleporting.getUniqueId(), countdown);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        String name = cmd.getName().toLowerCase();
        switch (name) {
            case "tpa":
            case "tpahere": {
                if (args.length != 1) {
                    player.sendMessage("§cUsage: /" + name + " <player>");
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || target == player) {
                    player.sendMessage("§cInvalid target.");
                    return true;
                }

                if (activeCountdowns.containsKey(player.getUniqueId())) {
                    player.sendMessage("§cYou already have a teleport in progress.");
                    return true;
                }

                requests.put(target.getUniqueId(), new TeleportRequest(player.getUniqueId(), name.equals("tpahere")));
                player.sendMessage("§aRequest sent to " + target.getName());
                String type = name.equals("tpahere") ? "wants to teleport you to them" : "wants to teleport to you";
                target.sendMessage("§e" + player.getName() + " " + type + ". Use §a/tpaccept§e or §c/tpdeny§e.");
                target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                return true;
            }

            case "tpaccept": {
                TeleportRequest req = requests.remove(player.getUniqueId());
                if (req == null) {
                    player.sendMessage("§cNo pending teleport requests.");
                    return true;
                }

                Player requester = Bukkit.getPlayer(req.requester);
                if (requester == null) {
                    player.sendMessage("§cThe requester is no longer online.");
                    return true;
                }

                player.sendMessage("§aTeleport request accepted.");
                requester.sendMessage("§aYour teleport request was accepted.");
                if (req.here) {
                    startTeleportCountdown(player, requester); // bring requester to player
                } else {
                    startTeleportCountdown(requester, player); // requester goes to player
                }
                return true;
            }

            case "tpdeny": {
                if (requests.remove(player.getUniqueId()) != null) {
                    player.sendMessage("§cTeleport request denied.");
                } else {
                    player.sendMessage("§cNo pending teleport requests.");
                }
                return true;
            }

            case "tpcancel": {
                UUID requester = player.getUniqueId();
                boolean found = requests.entrySet().removeIf(entry -> entry.getValue().requester.equals(requester));
                if (found) {
                    player.sendMessage("§eTeleport request cancelled.");
                } else {
                    player.sendMessage("§cNo active request to cancel.");
                }
                return true;
            }

            default:
                return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if ((label.equalsIgnoreCase("tpa") || label.equalsIgnoreCase("tpahere")) && args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .toList();
        }
        return Collections.emptyList();
    }
}