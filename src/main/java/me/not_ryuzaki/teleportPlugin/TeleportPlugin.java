package me.not_ryuzaki.teleportPlugin;

import org.bukkit.plugin.java.JavaPlugin;

public final class TeleportPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        TeleportCommandHandler handler = new TeleportCommandHandler(this);
        getCommand("tpa").setExecutor(handler);
        getCommand("tpa").setTabCompleter(handler);
        getCommand("tpahere").setExecutor(handler);
        getCommand("tpahere").setTabCompleter(handler);
        getCommand("tpaccept").setExecutor(handler);
        getCommand("tpdeny").setExecutor(handler);
        getCommand("tpcancel").setExecutor(handler);

        getCommand("spawn").setExecutor(new SpawnCommand(this, handler.getActiveCountdowns()));
        getServer().getPluginManager().registerEvents(new JoinListener(), this);
        getServer().getPluginManager().registerEvents(new RespawnListener(), this);
    }
}
