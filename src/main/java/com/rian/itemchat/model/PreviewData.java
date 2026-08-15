package com.rian.itemchat.model;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class PreviewData {

    public enum Type { ITEM, INVENTORY, POSITION }

    private final Type type;
    private final UUID owner;
    private final long createdAt;

    private ItemStack item;
    private ItemStack[] contents;
    private Location location;

    private PreviewData(Type type, UUID owner) {
        this.type = type;
        this.owner = owner;
        this.createdAt = System.currentTimeMillis();
    }

    public static PreviewData ofItem(UUID owner, ItemStack item) {
        PreviewData data = new PreviewData(Type.ITEM, owner);
        data.item = item.clone();
        return data;
    }

    public static PreviewData ofInventory(UUID owner, ItemStack[] contents) {
        PreviewData data = new PreviewData(Type.INVENTORY, owner);
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        data.contents = copy;
        return data;
    }

    public static PreviewData ofPosition(UUID owner, Location location) {
        PreviewData data = new PreviewData(Type.POSITION, owner);
        data.location = location.clone();
        return data;
    }

    public Type getType() { return type; }
    public UUID getOwner() { return owner; }
    public long getCreatedAt() { return createdAt; }
    public ItemStack getItem() { return item; }
    public ItemStack[] getContents() { return contents; }
    public Location getLocation() { return location; }
}
