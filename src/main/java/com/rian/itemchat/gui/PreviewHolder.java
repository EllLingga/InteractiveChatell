package com.rian.itemchat.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marker holder used to identify ItemChat's read-only preview GUIs. */
public class PreviewHolder implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        // Bukkit fills this in when the inventory is created with Bukkit.createInventory(holder, ...)
        return null;
    }
}
