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
 */
public class TextureCache {

    private static final String ASSET_BASE =
            "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/%s/assets/minecraft/textures/%s/%s.png";

    private final Plugin plugin;
    private final File cacheDir;
    private final String mcVersion;
    private final Map<Material, BufferedImage> memoryCache = new ConcurrentHashMap<>();
    private BufferedImage missingTexture;

    public TextureCache(Plugin plugin, String mcVersion) {
        this.plugin = plugin;
        this.mcVersion = mcVersion;
        this.cacheDir = new File(plugin.getDataFolder(), "textures");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
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

    private BufferedImage download(String category, String name, File saveTo) {
        try {
            URL url = new URL(String.format(ASSET_BASE, mcVersion, category, name));
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
        return missingTexture;
    }
}
