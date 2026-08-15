package com.rian.itemchat.render;

import org.bukkit.inventory.ItemStack;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class InventoryImageRenderer {

    private static final int SLOT = 48;
    private static final int PADDING = 12;
    private static final int COLS = 9;

    private final TextureCache textureCache;

    public InventoryImageRenderer(TextureCache textureCache) {
        this.textureCache = textureCache;
    }

    /** contents: 0-35 main storage (rows 0-2 storage + row 3 hotbar), 36-39 armor, 40 offhand */
    public File render(ItemStack[] contents, File outputFile) throws IOException {
        int mainRows = 4;
        int width = PADDING * 2 + COLS * SLOT;
        int height = PADDING * 3 + mainRows * SLOT + SLOT;

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        g.setColor(new Color(30, 30, 30));
        g.fillRoundRect(0, 0, width, height, 16, 16);

        // armor + offhand row (top)
        for (int i = 0; i < 5 && 36 + i < contents.length; i++) {
            int x = PADDING + i * SLOT;
            int y = PADDING;
            drawSlot(g, x, y, contents[36 + i]);
        }

        // main storage + hotbar (0-35)
        int startY = PADDING * 2 + SLOT;
        for (int i = 0; i < Math.min(36, contents.length); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = PADDING + col * SLOT;
            int y = startY + row * SLOT;
            drawSlot(g, x, y, contents[i]);
        }

        g.dispose();
        ImageIO.write(canvas, "png", outputFile);
        return outputFile;
    }

    private void drawSlot(Graphics2D g, int x, int y, ItemStack item) {
        int size = SLOT - 4;
        g.setColor(new Color(139, 139, 139));
        g.fillRect(x, y, size, size);
        g.setColor(new Color(55, 55, 55));
        g.drawRect(x, y, size, size);

        if (item == null || item.getType().isAir()) {
            return;
        }

        BufferedImage icon = textureCache.getTexture(item.getType());
        int iconSize = size - 10;
        g.drawImage(icon, x + 5, y + 5, iconSize, iconSize, null);

        if (item.getAmount() > 1) {
            String text = String.valueOf(item.getAmount());
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(text);
            int tx = x + SLOT - tw - 8;
            int ty = y + SLOT - 10;
            g.setColor(Color.BLACK);
            g.drawString(text, tx + 1, ty + 1);
            g.setColor(Color.WHITE);
            g.drawString(text, tx, ty);
        }
    }
}
