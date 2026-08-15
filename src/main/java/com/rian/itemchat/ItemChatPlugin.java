package com.rian.itemchat;

import com.rian.itemchat.discord.DiscordHook;
import com.rian.itemchat.gui.PreviewCommand;
import com.rian.itemchat.gui.PreviewGuiListener;
import org.bukkit.plugin.java.JavaPlugin;

public class ItemChatPlugin extends JavaPlugin {

    private PreviewStore previewStore;
    private DiscordHook discordHook;

    @Override
    public void onEnable() {
        this.previewStore = new PreviewStore();

        // Chat tag ([i] [item] [inv] [pos]) listener
        getServer().getPluginManager().registerEvents(new ChatTagListener(this, previewStore), this);

        // GUI click-preview command + inventory click blocker
        getServer().getPluginManager().registerEvents(new PreviewGuiListener(), this);
        PreviewCommand previewCommand = new PreviewCommand(previewStore);
        getCommand("itemchat").setExecutor(previewCommand);

        // Periodic cleanup of old click tokens
        getServer().getScheduler().runTaskTimerAsynchronously(this, previewStore::cleanup, 20L * 60, 20L * 60);

        // Optional DiscordSRV relay - only activates if DiscordSRV is installed & enabled
        if (getServer().getPluginManager().isPluginEnabled("DiscordSRV")) {
            this.discordHook = new DiscordHook(this);
            this.discordHook.register();
            getLogger().info("DiscordSRV detected - item/inventory previews will be relayed to Discord.");
        } else {
            getLogger().info("DiscordSRV not found - running with in-game chat previews only.");
        }

        getLogger().info("ItemChat enabled.");
    }

    @Override
    public void onDisable() {
        if (discordHook != null) {
            discordHook.unregister();
        }
    }
}
