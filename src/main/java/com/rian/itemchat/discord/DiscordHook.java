package com.rian.itemchat.discord;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Bridges chat-preview messages ([i]/[item]/[inv]/[pos]) to DiscordSRV's main text channel,
 * including image attachments (item icon / inventory grid screenshot).
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
     * was sent in-game. Sends a matching message (with image attachments, if any) to Discord.
     */
    public static void relayIfPresent(Plugin plugin, Player player, String plainMessage, List<File> images) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("DiscordSRV")) {
            return;
        }
        try {
            Class<?> discordSrvClass = Class.forName("github.scarsz.discordsrv.DiscordSRV");

            Method getPluginMethod = discordSrvClass.getMethod("getPlugin");
            Object discordSrvInstance = getPluginMethod.invoke(null);
            if (discordSrvInstance == null) return;

            Method getMainTextChannelMethod = discordSrvClass.getMethod("getMainTextChannel");
            Object channel = getMainTextChannelMethod.invoke(discordSrvInstance);
            if (channel == null) {
                return; // main channel isn't configured in DiscordSRV's config.yml
            }

            String formatted = "**" + player.getName() + "** " + plainMessage;

            Method sendMessageMethod = findMethod(channel.getClass(), "sendMessage", CharSequence.class);
            if (sendMessageMethod == null) {
                plugin.getLogger().warning("Could not find sendMessage(CharSequence) on DiscordSRV's channel object.");
                return;
            }
            Object action = sendMessageMethod.invoke(channel, formatted);
            if (action == null) return;

            if (images != null && !images.isEmpty()) {
                action = attachImages(action, images);
                if (action == null) return; // attaching failed, error already logged
            }

            Method queueMethod = action.getClass().getMethod("queue");
            queueMethod.invoke(action);
        } catch (ClassNotFoundException e) {
            // DiscordSRV isn't actually on the classpath despite being "enabled" - ignore
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to relay chat preview to Discord: " + t);
        }
    }

    /** Attaches PNG files to a MessageCreateAction via reflection (JDA's FileUpload API). */
    private static Object attachImages(Object action, List<File> images) {
        try {
            Class<?> fileUploadClass = Class.forName("net.dv8tion.jda.api.utils.FileUpload");
            Method fromDataMethod = fileUploadClass.getMethod("fromData", File.class, String.class);

            Object array = Array.newInstance(fileUploadClass, images.size());
            for (int i = 0; i < images.size(); i++) {
                File file = images.get(i);
                Object fileUpload = fromDataMethod.invoke(null, file, file.getName());
                Array.set(array, i, fileUpload);
            }

            Method addFilesMethod = findMethod(action.getClass(), "addFiles", array.getClass());
            if (addFilesMethod == null) return action; // no attachment support found, just send text
            return addFilesMethod.invoke(action, new Object[]{array});
        } catch (ClassNotFoundException e) {
            return action; // JDA not available for some reason, fall back to text-only
        } catch (Throwable t) {
            return action;
        }
    }

    private static Method findMethod(Class<?> startClass, String name, Class<?> paramType) {
        Class<?> current = startClass;
        while (current != null) {
            Method m = tryGetMethod(current, name, paramType);
            if (m != null) return m;
            for (Class<?> iface : current.getInterfaces()) {
                m = tryGetMethod(iface, name, paramType);
                if (m != null) return m;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method tryGetMethod(Class<?> clazz, String name, Class<?> paramType) {
        try {
            return clazz.getMethod(name, paramType);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
