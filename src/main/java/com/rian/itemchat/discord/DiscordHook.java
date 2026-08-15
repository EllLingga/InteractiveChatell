package com.rian.itemchat.discord;

import github.scarsz.discordsrv.DiscordSRV;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.awt.Color;

/**
 * Bridges chat-preview messages ([i]/[item]/[inv]/[pos]) to DiscordSRV's main text channel.
 * Uses the DiscordSRV instance & bot that is ALREADY installed and configured on the server -
 * this plugin does not bundle or set up DiscordSRV itself, it only talks to it via its API.
 */
public class DiscordHook {

    private final Plugin plugin;

    public DiscordHook(Plugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        // Nothing to subscribe to right now - relay happens on demand via relayIfPresent().
        // Reserved so future versions can hook DiscordSRV's own event bus if needed.
    }

    public void unregister() {
        // no-op for now
    }

    /**
     * Called by ChatTagListener whenever a message containing [i]/[item]/[inv]/[pos]
     * was sent in-game. Sends a matching embed to Discord.
     */
    public static void relayIfPresent(Plugin plugin, Player player, String plainMessage, String originalMessage) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("DiscordSRV")) {
            return;
        }
        try {
            MessageChannel channel = DiscordSRV.getPlugin().getMainTextChannel();
            if (channel == null) {
                return; // DiscordSRV main channel isn't configured
            }

            EmbedBuilder embed = new EmbedBuilder();
            embed.setAuthor(player.getName(), null, "https://mc-heads.net/avatar/" + player.getName() + "/64");
            embed.setDescription(plainMessage);
            embed.setColor(new Color(85, 170, 255));
            embed.setFooter("via chat preview");

            channel.sendMessageEmbeds(embed.build()).queue();
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to relay chat preview to Discord: " + t.getMessage());
        }
    }
}
