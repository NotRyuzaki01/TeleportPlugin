package me.not_ryuzaki.teleportPlugin;

import me.not_ryuzaki.mainScorePlugin.Combat;
import net.md_5.bungee.api.ChatColor;
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

    private void startTeleportCountdown(Player teleporting, Player target) {
        final int[] seconds = {5};
        final Location startLoc = teleporting.getLocation().clone();
        final Location destination = target.getLocation().clone();

        BukkitRunnable old = activeCountdowns.remove(teleporting.getUniqueId());
        if (old != null) old.cancel();

        BukkitRunnable countdown = new BukkitRunnable() {
            @Override
            public void run() {
                if (!teleporting.isOnline() || !target.isOnline()) {
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

                    target.sendMessage("§cTeleport cancelled because " + teleporting.getName() + " moved!");
                    target.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cTeleport cancelled because " + teleporting.getName() + " moved!"));
                    target.playSound(target.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);

                    Combat.unregisterTeleportCallback(teleporting.getUniqueId());
                    activeCountdowns.remove(teleporting.getUniqueId());
                    cancel();
                    return;
                }

                if (seconds[0] == 0) {
                    teleporting.teleport(destination);
                    teleporting.sendMessage("§aTeleported!");
                    teleporting.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§aTeleported!"));
                    target.sendMessage("§aTeleport completed.");
                    target.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§aTeleport completed."));
                    teleporting.playSound(teleporting.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
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
                target.spigot().sendMessage(ChatMessageType.ACTION_BAR, message);

                teleporting.playSound(teleporting.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                target.playSound(target.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);

                seconds[0]--;
            }
        };

        countdown.runTaskTimer(plugin, 0L, 20L);
        activeCountdowns.put(teleporting.getUniqueId(), countdown);

        // Cancel on combat
        Combat.registerTeleportCancelCallback(teleporting.getUniqueId(), () -> {
            countdown.cancel();
            teleporting.sendMessage("§cTeleport cancelled — you entered combat!");
            teleporting.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cTeleport cancelled — in combat!"));
            teleporting.playSound(teleporting.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            activeCountdowns.remove(teleporting.getUniqueId());
        });
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
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cInvalid target."));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                }

                if (Combat.isInCombat(player) || Combat.isInCombat(target)) {
                    player.sendMessage("§cYou or the target is in combat!");
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cCan't request teleport while in combat!"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                }

                if (activeCountdowns.containsKey(player.getUniqueId())) {
                    player.sendMessage("§cYou already have a teleport in progress.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                }

                requests.put(target.getUniqueId(), new TeleportRequest(player.getUniqueId(), name.equals("tpahere")));

                // Send request messages
                TextComponent sentMsg = new TextComponent("Request sent to ");
                sentMsg.setColor(ChatColor.GREEN);
                TextComponent nameBlue = new TextComponent(target.getName());
                nameBlue.setColor(ChatColor.of("#0094FF"));
                sentMsg.addExtra(nameBlue);

                player.spigot().sendMessage(ChatMessageType.CHAT, sentMsg);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, sentMsg);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

                // Notify target
                TextComponent full = new TextComponent();
                full.addExtra(new TextComponent(ChatColor.of("#0094FF") + player.getName()));
                full.addExtra(new TextComponent(ChatColor.WHITE + " sent a "));
                full.addExtra(new TextComponent(ChatColor.of("#0094FF") + name));
                full.addExtra(new TextComponent(ChatColor.WHITE + " request. Use "));
                full.addExtra(new TextComponent(ChatColor.GREEN + "/tpaccept"));
                full.addExtra(new TextComponent(ChatColor.WHITE + " or "));
                full.addExtra(new TextComponent(ChatColor.RED + "/tpdeny"));

                target.spigot().sendMessage(ChatMessageType.CHAT, full);
                target.spigot().sendMessage(ChatMessageType.ACTION_BAR, full);
                target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                return true;
            }

            case "tpaccept": {
                TeleportRequest req = requests.remove(player.getUniqueId());
                if (req == null) {
                    player.sendMessage("§cNo pending teleport requests.");
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cNo pending teleport requests."));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                }

                Player requester = Bukkit.getPlayer(req.requester);
                if (requester == null) {
                    player.sendMessage("§cThe requester is no longer online.");
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cThe requester is no longer online."));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                }

                if (Combat.isInCombat(player) || Combat.isInCombat(requester)) {
                    player.sendMessage("§cTeleport cancelled — one of you is in combat!");
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cYou're in combat!"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    requester.sendMessage("§cTeleport cancelled — you or the target is in combat.");
                    requester.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cTeleport cancelled — in combat!"));
                    requester.playSound(requester.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                }

                player.sendMessage("§aTeleport request accepted.");
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                requester.sendMessage("§aYour teleport request was accepted.");
                requester.playSound(requester.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

                if (req.here) {
                    startTeleportCountdown(player, requester);
                } else {
                    startTeleportCountdown(requester, player);
                }
                return true;
            }

            case "tpdeny": {
                TeleportRequest req = requests.remove(player.getUniqueId());
                if (req != null) {
                    player.sendMessage("§cTeleport request denied.");
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cTeleport request denied."));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);

                    Player requester = Bukkit.getPlayer(req.requester);
                    if (requester != null) {
                        requester.sendMessage("§cYour teleport request was denied.");
                        requester.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cYour teleport request was denied."));
                        requester.playSound(requester.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    }
                } else {
                    player.sendMessage("§cNo pending teleport requests.");
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cNo pending teleport requests."));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
                return true;
            }

            case "tpcancel": {
                UUID requester = player.getUniqueId();
                boolean found = requests.entrySet().removeIf(entry -> entry.getValue().requester.equals(requester));
                if (found) {
                    player.sendMessage("§eTeleport request cancelled.");
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§eTeleport request cancelled."));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                } else {
                    player.sendMessage("§cNo active request to cancel.");
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cNo active request to cancel."));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
                return true;
            }

            default:
                return false;
        }
    }

    public Map<UUID, BukkitRunnable> getActiveCountdowns() {
        return activeCountdowns;
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
