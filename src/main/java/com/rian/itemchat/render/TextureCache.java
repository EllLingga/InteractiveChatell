package com.rian.itemchat.render;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Downloads flat 16x16 item/block icon textures from the public
 * "minecraft-assets" mirror on first use, and caches them on disk
 * (plugins/ItemChat/textures/) so subsequent renders don't need internet.
 *
 * Note: these are the flat inventory icons Minecraft itself uses/generates,
 * not a full 3D isometric block render - that would need a real rendering
 * engine which isn't practical inside a lightweight plugin.
 *
 * The mirror is organized as one git branch per Minecraft version (there is no
 * "master"/rolling branch). Rather than hardcoding a version (which would
 * silently go stale on every future update), this auto-detects the server's
 * own Minecraft version and tries that branch first, then progressively
 * shorter version prefixes as a best-effort fallback if the exact patch
 * version doesn't have a branch there yet - e.g. because it's newer than
 * anything the mirror has tagged.
 */
public class TextureCache {

    private static final String ASSET_BASE =
            "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/%s/assets/minecraft/textures/%s/%s.png";

    private final Plugin plugin;
    private final File cacheDir;
    private final List<String> candidateRefs;
    private final Map<Material, BufferedImage> memoryCache = new ConcurrentHashMap<>();
    private BufferedImage missingTexture;

    public TextureCache(Plugin plugin) {
        this.plugin = plugin;
        this.cacheDir = new File(plugin.getDataFolder(), "textures");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        this.candidateRefs = buildCandidateRefs(plugin);
        if (candidateRefs.isEmpty()) {
            plugin.getLogger().warning("ItemChat: could not auto-detect the server's Minecraft "
                    + "version; item icon downloads will use the placeholder icon.");
        } else {
            plugin.getLogger().info("ItemChat: fetching item icons for detected server version "
                    + candidateRefs.get(0) + (candidateRefs.size() > 1
                    ? " (falling back to " + String.join(", ", candidateRefs.subList(1, candidateRefs.size()))
                        + " if a texture isn't found there, e.g. items added after the mirror's last update)."
                    : "."));
        }
    }

    /** Kept for anyone constructing this directly with an explicit override (e.g. tests). */
    public TextureCache(Plugin plugin, String forcedMcVersion) {
        this.plugin = plugin;
        this.cacheDir = new File(plugin.getDataFolder(), "textures");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        List<String> refs = new ArrayList<>();
        if (forcedMcVersion != null && !forcedMcVersion.isEmpty()) {
            refs.add(forcedMcVersion);
            // Same best-effort shorter-prefix fallback as the auto-detected path, since
            // the mirror has no generic "master"/rolling branch to fall back to.
            String[] parts = forcedMcVersion.split("\\.");
            for (int len = parts.length - 1; len >= 2; len--) {
                String shorter = String.join(".", java.util.Arrays.copyOfRange(parts, 0, len));
                if (!refs.contains(shorter)) {
                    refs.add(shorter);
                }
            }
        }
        this.candidateRefs = refs;
    }

    /**
     * Figures out the running server's Minecraft version, so this always tracks whatever
     * version the server actually is instead of a value baked in at build time.
     *
     * getBukkitVersion() is NOT reliable for this: on some server implementations/versions
     * it isn't a clean "1.20.4-R0.1-SNAPSHOT" string, and blindly cutting at the first '-'
     * can leave trailing build metadata attached (e.g. "26.1.2.build.74" instead of
     * "26.1.2"), which will never match one of the mirror's version-named branches.
     * getVersion() (e.g. "git-Paper-74 (MC: 26.1.2)") reliably contains the real vanilla
     * Minecraft version in parentheses, so that's parsed first and preferred.
     *
     * Also note: the mirror (InventivetalentDev/minecraft-assets) has no "master" branch -
     * it only has one branch per Minecraft version. So there is no safe generic fallback
     * ref; if the exact version branch doesn't exist (e.g. a very new release the mirror
     * hasn't tagged yet), we progressively try shorter/older version prefixes as a
     * best-effort fallback instead of a branch name that can never exist.
     */
    private static List<String> buildCandidateRefs(Plugin plugin) {
        List<String> refs = new ArrayList<>();
        String detected = null;
        try {
            // Prefer parsing "(MC: X.Y.Z)" out of getVersion() - this is the actual
            // vanilla Minecraft version and matches the mirror's branch naming.
            String fullVersion = plugin.getServer().getVersion(); // e.g. "git-Paper-74 (MC: 26.1.2)"
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("MC:\\s*([0-9]+(?:\\.[0-9]+)*)").matcher(fullVersion);
            if (m.find()) {
                detected = m.group(1);
            } else {
                // Fallback: extract a leading version-number pattern from getBukkitVersion(),
                // ignoring anything after it (build numbers, hashes, suffixes, etc.)
                String bukkitVersion = plugin.getServer().getBukkitVersion();
                java.util.regex.Matcher m2 = java.util.regex.Pattern
                        .compile("^([0-9]+(?:\\.[0-9]+)*)").matcher(bukkitVersion);
                if (m2.find()) {
                    detected = m2.group(1);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not auto-detect the server's Minecraft version for "
                    + "item icon downloads: " + e);
        }

        if (detected != null && !detected.isEmpty()) {
            refs.add(detected);
            // Best-effort fallback: if the mirror doesn't have a branch for this exact
            // patch version yet, progressively try shorter prefixes (e.g. "26.1.2" ->
            // "26.1"), since the mirror is more likely to have the minor-version branch.
            String[] parts = detected.split("\\.");
            for (int len = parts.length - 1; len >= 2; len--) {
                String shorter = String.join(".", java.util.Arrays.copyOfRange(parts, 0, len));
                if (!refs.contains(shorter)) {
                    refs.add(shorter);
                }
            }
        }
        return refs;
    }

    public BufferedImage getTexture(Material material) {
        BufferedImage cached = memoryCache.get(material);
        if (cached != null) {
            return cached;
        }

        String name = material.name().toLowerCase(Locale.ROOT);
        File file = new File(cacheDir, name + ".png");
        BufferedImage image = null;
        List<String> diagnostics = new ArrayList<>();

        try {
            if (file.exists()) {
                image = ImageIO.read(file);
            }
            if (image == null) {
                image = download("item", name, file, diagnostics);
            }
            if (image == null) {
                image = download("block", name, file, diagnostics);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load texture for " + name + ": " + e.getMessage());
        }

        if (image == null) {
            plugin.getLogger().warning("Could not fetch a texture for " + name + " from any mirror branch, "
                    + "using a placeholder icon instead. Attempts: " + String.join(" | ", diagnostics));
            image = getMissingTexture();
        }
        memoryCache.put(material, image);
        return image;
    }

    /** Tries each candidate mirror branch (detected version, then shorter version prefixes) until one has the texture. */
    private BufferedImage download(String category, String name, File saveTo, List<String> diagnostics) {
        for (String ref : candidateRefs) {
            BufferedImage image = downloadFromRef(ref, category, name, saveTo, diagnostics);
            if (image != null) {
                return image;
            }
        }
        return null;
    }

    private BufferedImage downloadFromRef(String ref, String category, String name, File saveTo, List<String> diagnostics) {
        String label = ref + "/" + category + "/" + name;
        try {
            URL url = new URL(String.format(ASSET_BASE, ref, category, name));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("User-Agent", "ItemChat-Plugin");
            int code = conn.getResponseCode();
            if (code != 200) {
                diagnostics.add(label + " -> HTTP " + code);
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                Files.write(saveTo.toPath(), bytes);
                return ImageIO.read(new ByteArrayInputStream(bytes));
            }
        } catch (Exception e) {
            diagnostics.add(label + " -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    private BufferedImage getMissingTexture() {
        if (missingTexture == null) {
            missingTexture = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = missingTexture.createGraphics();
            g.setColor(new Color(255, 0, 255));
            g.fillRect(0, 0, 8, 8);
            g.fillRect(8, 8, 8, 8);
            g.setColor(Color.BLACK);
            g.fillRect(8, 0, 8, 8);
            g.fillRect(0, 8, 8, 8);
            g.dispose();
        }
        return missingTexture;
    }
}
