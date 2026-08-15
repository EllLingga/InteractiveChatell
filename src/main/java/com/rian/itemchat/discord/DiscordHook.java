package com.rian.itemchat.discord;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Bridges chat-preview messages ([i]/[item]/[inv]/[pos]) to DiscordSRV's main text channel.
 *
 * This talks to the ALREADY installed & configured DiscordSRV plugin (bot token, channel IDs,
 * etc. all set up by the user) purely through Java reflection. That means this plugin does not
 * need DiscordSRV's or JDA's classes at compile time at all - it only needs them to exist on
 * the server at runtime, which they do because DiscordSRV is already installed there.
 */
public class DiscordHook {

    private final Plugin plugin;

    public DiscordHook(Plugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        // Nothing to subscribe to right now - relay happens on demand via relayIfPresent().
    }

    public void unregister() {
        // no-op
    }

    /**
     * Called by ChatTagListener whenever a message containing [i]/[item]/[inv]/[pos]
     * was sent in-game. Sends a matching plain-text message to Discord.
     */
    public static void relayIfPresent(Plugin plugin, Player player, String plainMessage, String originalMessage) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("DiscordSRV")) {
            return;
        }
        try {
            Class<?> discordSrvClass = Class.forName("github.scarsz.discordsrv.DiscordSRV");

            // DiscordSRV.getPlugin()
            Method getPluginMethod = discordSrvClass.getMethod("getPlugin");
            Object discordSrvInstance = getPluginMethod.invoke(null);
            if (discordSrvInstance == null) return;

            // discordSrvInstance.getMainTextChannel()
            Method getMainTextChannelMethod = discordSrvClass.getMethod("getMainTextChannel");
            Object channel = getMainTextChannelMethod.invoke(discordSrvInstance);
            if (channel == null) {
                return; // main channel isn't configured in DiscordSRV's config.yml
            }

            String formatted = "**" + player.getName() + "** " + plainMessage;

            // channel.sendMessage(CharSequence) -> MessageCreateAction
            Method sendMessageMethod = findSendMessageMethod(channel.getClass());
            if (sendMessageMethod == null) {
                plugin.getLogger().warning("Could not find sendMessage(CharSequence) on DiscordSRV's channel object.");
                return;
            }
            Object action = sendMessageMethod.invoke(channel, formatted);
            if (action == null) return;

            // action.queue() - fires it off asynchronously, we don't need the result
            Method queueMethod = action.getClass().getMethod("queue");
            queueMethod.invoke(action);
        } catch (ClassNotFoundException e) {
            // DiscordSRV isn't actually on the classpath despite being "enabled" - ignore
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to relay chat preview to Discord: " + t);
        }
    }

    private static Method findSendMessageMethod(Class<?> channelClass) {
        // Walk the class + all interfaces looking for sendMessage(CharSequence)
        Class<?> current = channelClass;
        while (current != null) {
            Method m = tryGetMethod(current);
            if (m != null) return m;
            for (Class<?> iface : current.getInterfaces()) {
                m = tryGetMethod(iface);
                if (m != null) return m;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method tryGetMethod(Class<?> clazz) {
        try {
            return clazz.getMethod("sendMessage", CharSequence.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
