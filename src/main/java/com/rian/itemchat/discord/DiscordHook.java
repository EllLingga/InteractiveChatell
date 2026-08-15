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
                // DiscordSRV shades/relocates JDA (e.g. net.dv8tion.jda -> github.scarsz.discordsrv.dependencies.jda)
                // to avoid classpath conflicts, so we can't hardcode "net.dv8tion.jda...". Derive the real,
                // possibly-relocated JDA base package from the channel object's own class name instead.
                String jdaBasePackage = detectJdaBasePackage(channel.getClass());
                action = attachImages(plugin, channel.getClass().getClassLoader(), jdaBasePackage, action, images);
            }

            Method queueMethod = action.getClass().getMethod("queue");
            queueMethod.invoke(action);
        } catch (ClassNotFoundException e) {
            // DiscordSRV isn't actually on the classpath despite being "enabled" - ignore
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to relay chat preview to Discord: " + t);
        }
    }

    /**
     * Figures out the real (possibly relocated/shaded) base package of JDA at runtime by
     * inspecting the fully-qualified class name of a known JDA object (e.g. the TextChannel
     * we got from DiscordSRV). DiscordSRV's shaded jar relocates "net.dv8tion.jda" to
     * "github.scarsz.discordsrv.dependencies.jda", so hardcoding "net.dv8tion.jda" breaks.
     * Falls back to the unshaded "net.dv8tion.jda" if no ".jda." segment is found.
     */
    private static String detectJdaBasePackage(Class<?> jdaObjectClass) {
        String name = jdaObjectClass.getName();
        int idx = name.indexOf(".jda.");
        if (idx >= 0) {
            return name.substring(0, idx + 4); // include trailing ".jda"
        }
        return "net.dv8tion.jda";
    }

    /**
     * Attaches PNG files to a MessageCreateAction via reflection (JDA's FileUpload API).
     * Always returns a non-null action: if attaching fails for any reason, the ORIGINAL
     * action is returned (so the text message still sends) but the failure is logged so
     * it's actually visible instead of silently dropping the images.
     */
    private static Object attachImages(Plugin plugin, ClassLoader jdaClassLoader, String jdaBasePackage,
                                        Object action, List<File> images) {
        try {
            Class<?> fileUploadClass = Class.forName(
                    jdaBasePackage + ".api.utils.FileUpload", true, jdaClassLoader);
            Method fromDataMethod = fileUploadClass.getMethod("fromData", File.class, String.class);

            Object array = Array.newInstance(fileUploadClass, images.size());
            for (int i = 0; i < images.size(); i++) {
                File file = images.get(i);
                Object fileUpload = fromDataMethod.invoke(null, file, file.getName());
                Array.set(array, i, fileUpload);
            }

            // Look for addFiles(FileUpload...) on the PUBLIC interface chain (MessageCreateRequest),
            // not on the concrete (often non-public) implementation class - invoking a Method whose
            // declaring class isn't public throws IllegalAccessException even though the method
            // itself is public. Also force-open access just in case.
            Method addFilesMethod = findPublicInterfaceMethod(action.getClass(), "addFiles", array.getClass());
            if (addFilesMethod == null) {
                addFilesMethod = findMethod(action.getClass(), "addFiles", array.getClass());
            }
            if (addFilesMethod == null) {
                plugin.getLogger().warning("Could not find addFiles(FileUpload...) on "
                        + action.getClass() + " - images will NOT be sent to Discord, only text.");
                return action;
            }
            addFilesMethod.setAccessible(true);
            Object result = addFilesMethod.invoke(action, new Object[]{array});
            return result != null ? result : action;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("JDA's FileUpload class not found (looked for "
                    + jdaBasePackage + ".api.utils.FileUpload) - images will NOT be sent to Discord: " + e);
            return action;
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to attach images to Discord message "
                    + "(falling back to text-only): " + t);
            return action;
        }
    }

    /**
     * Same lookup as findMethod, but returns the Method object taken from the first PUBLIC
     * interface in the hierarchy that declares it, so Method#invoke doesn't choke on a
     * package-private implementation class (common with JDA's `internal.*` impl classes).
     */
    private static Method findPublicInterfaceMethod(Class<?> startClass, String name, Class<?> paramType) {
        Class<?> current = startClass;
        while (current != null) {
            for (Class<?> iface : getAllInterfaces(current)) {
                if (!java.lang.reflect.Modifier.isPublic(iface.getModifiers())) continue;
                Method m = tryGetMethod(iface, name, paramType);
                if (m != null) return m;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static List<Class<?>> getAllInterfaces(Class<?> clazz) {
        List<Class<?>> result = new java.util.ArrayList<>();
        for (Class<?> iface : clazz.getInterfaces()) {
            result.add(iface);
            result.addAll(getAllInterfaces(iface));
        }
        return result;
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
