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
 * The mirror is organized as one git branch per Minecraft version. Rather than
 * hardcoding a version (which would silently go stale on every future update),
 * this auto-detects the server's own Minecraft version and tries that branch
 * first, falling back to the mirror's "master" branch (kept close to the
 * newest release) if the exact version doesn't have a branch there - e.g.
 * because it's newer than anything the mirror has tagged yet.
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
        plugin.getLogger().info("ItemChat: fetching item icons for detected server version "
                + candidateRefs.get(0) + " (falling back to the mirror's 'master' branch for "
                + "anything not found there, e.g. items added after the mirror's last update).");
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
        }
        if (!refs.contains("master")) {
            refs.add("master");
        }
        this.candidateRefs = refs;
    }

    /**
     * Figures out the running server's Minecraft version from Bukkit itself (e.g.
     * getBukkitVersion() = "1.20.4-R0.1-SNAPSHOT" -> "1.20.4"), so this always tracks
     * whatever version the server actually is instead of a value baked in at build time.
     */
    private static List<String> buildCandidateRefs(Plugin plugin) {
        List<String> refs = new ArrayList<>();
        try {
            String bukkitVersion = plugin.getServer().getBukkitVersion(); // e.g. "1.20.4-R0.1-SNAPSHOT"
            int dashIdx = bukkitVersion.indexOf('-');
            String detected = dashIdx > 0 ? bukkitVersion.substring(0, dashIdx) : bukkitVersion;
            if (!detected.isEmpty()) {
                refs.add(detected);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not auto-detect the server's Minecraft version for "
                    + "item icon downloads, falling back to the mirror's 'master' branch: " + e);
        }
        if (!refs.contains("master")) {
            refs.add("master"); // mirror's rolling branch, usually close to the newest release
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

        try {
            if (file.exists()) {
                image = ImageIO.read(file);
            }
            if (image == null) {
                image = download("item", name, file);
            }
            if (image == null) {
                image = download("block", name, file);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load texture for " + name + ": " + e.getMessage());
        }

        if (image == null) {
            image = getMissingTexture();
        }
        memoryCache.put(material, image);
        return image;
    }

    /** Tries each candidate mirror branch (detected server version, then "master") until one has the texture. */
    private BufferedImage download(String category, String name, File saveTo) {
        for (String ref : candidateRefs) {
            BufferedImage image = downloadFromRef(ref, category, name, saveTo);
            if (image != null) {
                return image;
            }
        }
        return null;
    }

    private BufferedImage downloadFromRef(String ref, String category, String name, File saveTo) {
        try {
            URL url = new URL(String.format(ASSET_BASE, ref, category, name));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("User-Agent", "ItemChat-Plugin");
            if (conn.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                Files.write(saveTo.toPath(), bytes);
                return ImageIO.read(new ByteArrayInputStream(bytes));
            }
        } catch (Exception e) {
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
        re