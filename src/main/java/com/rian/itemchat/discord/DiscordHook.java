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
                action = attachImages(plugin, channel.getClass().getClassLoader(), action, images);
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
     * Attaches PNG files to the Discord message action, trying BOTH JDA API shapes since
     * different JDA versions (and DiscordSRV's bundled version specifically) differ here:
     *  - JDA 5+: FileUpload.fromData(...) + action.addFiles(FileUpload...)
     *  - JDA 4.x (what DiscordSRV currently bundles): action.addFile(File, String, AttachmentOption...)
     *    called once per file - note the trailing varargs, which compiles to a 3rd array
     *    parameter, so a plain 2-arg (File, String) lookup never matches the real method.
     * Always returns a non-null action: if BOTH attempts fail, the ORIGINAL action is
     * returned (so the text message still sends), and the failure is logged so it's
     * actually visible instead of silently dropping the images.
     */
    private static Object attachImages(Plugin plugin, ClassLoader jdaClassLoader, Object action, List<File> images) {
        String jdaBasePackage = detectJdaBasePackage(action.getClass());

        Object viaFileUpload = tryAttachViaFileUpload(jdaClassLoader, jdaBasePackage, action, images);
        if (viaFileUpload != null) return viaFileUpload;

        Object viaAddFile = tryAttachViaLegacyAddFile(jdaClassLoader, jdaBasePackage, action, images);
        if (viaAddFile != null) return viaAddFile;

        plugin.getLogger().warning("Could not attach images to the Discord message - tried both the "
                + "JDA5 FileUpload API and the legacy JDA4 addFile(File, String, AttachmentOption...) "
                + "API and neither worked against " + action.getClass() + ". Only text will be sent to Discord.");
        return action;
    }

    /** JDA 5+ style: FileUpload.fromData(File, String) + action.addFiles(FileUpload...). Returns null on any failure. */
    private static Object tryAttachViaFileUpload(ClassLoader jdaClassLoader, String jdaBasePackage,
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

            Method addFilesMethod = findPublicInterfaceMethod(action.getClass(), "addFiles", array.getClass());
            if (addFilesMethod == null) {
                addFilesMethod = findMethod(action.getClass(), "addFiles", array.getClass());
            }
            if (addFilesMethod == null) return null;

            addFilesMethod.setAccessible(true);
            Object result = addFilesMethod.invoke(action, new Object[]{array});
            return result != null ? result : action;
        } catch (Throwable t) {
            return null; // this API shape isn't available - caller will try the legacy one
        }
    }

    /**
     * JDA 4.x style (what DiscordSRV currently bundles): action.addFile(File, String, AttachmentOption...)
     * once per image. The trailing varargs is a real array parameter at the bytecode level, so we
     * build an empty AttachmentOption[] (no spoiler/etc options needed) and pass that as the 3rd arg.
     * Returns null on any failure so the caller knows to fall back further (or give up and log).
     */
    private static Object tryAttachViaLegacyAddFile(ClassLoader jdaClassLoader, String jdaBasePackage,
                                                      Object action, List<File> images) {
        try {
            Class<?> attachmentOptionClass = Class.forName(
                    jdaBasePackage + ".api.utils.AttachmentOption", true, jdaClassLoader);
            Object emptyOptions = Array.newInstance(attachmentOptionClass, 0);

            Object current = action;
            for (File file : images) {
                Method addFileMethod = findPublicInterfaceMethod(
                        current.getClass(), "addFile", File.class, String.class, emptyOptions.getClass());
                if (addFileMethod == null) {
                    addFileMethod = findMethod(
                            current.getClass(), "addFile", File.class, String.class, emptyOptions.getClass());
                }
                if (addFileMethod == null) return null; // this API shape isn't available either
                addFileMethod.setAccessible(true);
                Object result = addFileMethod.invoke(current, file, file.getName(), emptyOptions);
                if (result != null) current = result;
            }
            return current;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Same lookup as findMethod, but returns the Method object taken from the first PUBLIC
     * interface in the hierarchy that declares it, so Method#invoke doesn't choke on a
     * package-private implementation class (common with JDA's `internal.*` impl classes).
     */
    private static Method findPublicInterfaceMethod(Class<?> startClass, String name, Class<?>... paramTypes) {
        Class<?> current = startClass;
        while (current != null) {
            for (Class<?> iface : getAllInterfaces(current)) {
                if (!java.lang.reflect.Modifier.isPublic(iface.getModifiers())) continue;
                Method m = tryGetMethod(iface, name, paramTypes);
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

    private static Method findMethod(Class<?> startClass, String name, Class<?>... paramTypes) {
        Class<?> current = startClass;
        while (current != null) {
            Method m = tryGetMethod(current, name, paramTypes);
            if (m != null) return m;
            for (Class<?> iface : current.getInterfaces()) {
                m = tryGetMethod(iface, name, paramTypes);
                if (m != null) return m;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method tryGetMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
