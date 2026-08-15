package com.rian.itemchat.gui;

import com.rian.itemchat.PreviewStore;
import com.rian.itemchat.model.PreviewData;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class PreviewCommand implements CommandExecutor {

    private final PreviewStore store;

    public PreviewCommand(PreviewStore store) {
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("view")) {
            return true;
        }

        PreviewData data = store.get(args[1]);
        if (data == null) {
            player.sendMessage(ChatColor.RED + "This preview has expired.");
            return true;
        }

        switch (data.getType()) {
            case ITEM -> openItem(player, data.getItem());
            case INVENTORY -> openInventory(player, data);
            case POSITION -> {
                // position tags don't open a GUI, they only copy coordinates on click
            }
        }
        return true;
    }

    private void openItem(Player player, ItemStack item) {
        Inventory inv = Bukkit.createInventory(new PreviewHolder(), 9, ChatColor.DARK_GRAY + "Item Preview");
        inv.setItem(4, item);
        player.openInventory(inv);
    }

    private void openInventory(Player player, PreviewData data) {
        ItemStack[] contents = data.getContents();
        // 54 slots: first 36 = main storage, next 4 = armor, last = offhand
        Inventory inv = Bukkit.createInventory(new PreviewHolder(), 54,
                ChatColor.DARK_GRAY + "Inventory Preview");

        for (int i = 0; i < Math.min(36, contents.length); i++) {
            inv.setItem(i, contents[i]);
        }
        String[] labels = {"Boots", "Leggings", "Chestplate", "Helmet", "Offhand"};
        for (int i = 0; i < labels.length && 36 + i < contents.length; i++) {
            ItemStack it = contents[36 + i];
            if (it != null) {
                inv.setItem(45 + i, it);
            }
        }
        player.openInventory(inv);
    }
}
