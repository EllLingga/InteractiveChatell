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

public class ItemImageRenderer {

    private static final int SLOT_SIZE = 128;
    private static final int ICON_SIZE = 96;

    private final TextureCache textureCache;

    public ItemImageRenderer(TextureCache textureCache) {
        this.textureCache = textureCache;
    }

    public File render(ItemStack item, File outputFile) throws IOException {
        BufferedImage canvas = new BufferedImage(SLOT_SIZE, SLOT_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        g.setColor(new Color(139, 139, 139));
        g.fillRoundRect(0, 0, SLOT_SIZE, SLOT_SIZE, 14, 14);
        g.setColor(new Color(55, 55, 55));
        g.drawRoundRect(1, 1, SLOT_SIZE - 3, SLOT_SIZE - 3, 14, 14);

        BufferedImage icon = textureCache.getTexture(item.getType());
        int offset = (SLOT_SIZE - ICON_SIZE) / 2;
        g.drawImage(icon, offset, offset, ICON_SIZE, ICON_SIZE, null);

        if (item.getAmount() > 1) {
            drawCount(g, item.getAmount(), SLOT_SIZE, 26);
        }

        g.dispose();
        ImageIO.write(canvas, "png", outputFile);
        return outputFile;
    }

    static void drawCount(Graphics2D g, int amount, int slotSize, int fontSize) {
        String text = String.valueOf(amount);
        g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int x = slotSize - textWidth - 10;
        int y = slotSize - 12;

        g.setColor(Color.BLACK);
        g.drawString(text, x + 2, y + 2);
        g.setColor(Color.WHITE);
        g.drawString(text, x, y);
    }
}
