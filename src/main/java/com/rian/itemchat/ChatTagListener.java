package com.rian.itemchat;

import com.rian.itemchat.discord.DiscordHook;
import com.rian.itemchat.model.PreviewData;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatTagListener implements Listener {

    // [i] and [item] -> item in hand, [inv] -> inventory snapshot, [pos] -> current position
    private static final Pattern TAG_PATTERN =
            Pattern.compile("\\[item\\]|\\[i\\]|\\[inv\\]|\\[pos\\]", Pattern.CASE_INSENSITIVE);

    private final Plugin plugin;
    private final PreviewStore store;

    public ChatTagListener(Plugin plugin, PreviewStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("itemchat.use")) {
            return;
        }

        String message = event.getMessage();
        Matcher matcher = TAG_PATTERN.matcher(message);
        if (!matcher.find()) {
            return; // nothing to do, let the message go through normally
        }
        matcher.reset();

        // We are taking over formatting for this message, cancel the plain-text broadcast
        event.setCancelled(true);

        ComponentBuilder builder = new ComponentBuilder("");
        StringBuilder plainForDiscord = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            String before = message.substring(lastEnd, matcher.start());
            if (!before.isEmpty()) {
                builder.append(before, ComponentBuilder.FormatRetention.NONE);
                plainForDiscord.append(before);
            }

            String tag = matcher.group().toLowerCase(Locale.ROOT);
            switch (tag) {
                case "[item]":
                case "[i]": {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    BaseComponent comp = buildItemComponent(player, hand);
                    builder.append(comp.duplicate(), ComponentBuilder.FormatRetention.NONE);
                    plainForDiscord.append("[").append(itemName(hand)).append("]");
                    break;
                }
                case "[inv]": {
                    ItemStack[] contents = fullInventorySnapshot(player);
                    BaseComponent comp = buildInventoryComponent(player, contents);
                    builder.append(comp.duplicate(), ComponentBuilder.FormatRetention.NONE);
                    plainForDiscord.append("[Inventory]");
                    break;
                }
                case "[pos]": {
                    Location loc = player.getLocation();
                    BaseComponent comp = buildPositionComponent(player, loc);
                    builder.append(comp.duplicate(), ComponentBuilder.FormatRetention.NONE);
                    plainForDiscord.append(String.format("[%d, %d, %d]",
                            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
                    break;
                }
                default:
                    break;
            }
            lastEnd = matcher.end();
        }

        String tail = message.substring(lastEnd);
        if (!tail.isEmpty()) {
            builder.append(tail, ComponentBuilder.FormatRetention.NONE);
            plainForDiscord.append(tail);
        }

        BaseComponent[] finalMessage = new ComponentBuilder()
                .append("<" + player.getDisplayName() + "> ", ComponentBuilder.FormatRetention.NONE)
                .append(builder.create())
                .create();

        for (Player recipient : plugin.getServer().getOnlinePlayers()) {
            recipient.spigot().sendMessage(finalMessage);
        }
        plugin.getServer().getConsoleSender().spigot().sendMessage(finalMessage);

        // Relay to Discord if the hook is active
        DiscordHook.relayIfPresent(plugin, player, plainForDiscord.toString(), message);
    }

    private BaseComponent buildItemComponent(Player owner, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            TextComponent comp = new TextComponent("[no item]");
            comp.setColor(ChatColor.GRAY);
            comp.setItalic(true);
            return comp;
        }
        String token = store.save(PreviewData.ofItem(owner.getUniqueId(), item));

        TextComponent comp = new TextComponent("[" + itemName(item) + "]");
        comp.setColor(ChatColor.AQUA);
        comp.setBold(true);
        comp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(hoverTextForItem(item)).create()));
        comp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/itemchat view " + token));
        return comp;
    }

    private BaseComponent buildInventoryComponent(Player owner, ItemStack[] contents) {
        String token = store.save(PreviewData.ofInventory(owner.getUniqueId(), contents));

        TextComponent comp = new TextComponent("[Inventory]");
        comp.setColor(ChatColor.GOLD);
        comp.setBold(true);
        comp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(owner.getName() + "'s inventory\n").color(ChatColor.YELLOW)
                        .append("Click to view", ComponentBuilder.FormatRetention.NONE).color(ChatColor.GRAY)
                        .create()));
        comp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/itemchat view " + token));
        return comp;
    }

    private BaseComponent buildPositionComponent(Player owner, Location loc) {
        String token = store.save(PreviewData.ofPosition(owner.getUniqueId(), loc));
        String coords = loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();

        TextComponent comp = new TextComponent("[" + coords + "]");
        comp.setColor(ChatColor.GREEN);
        comp.setBold(true);
        comp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("World: " + loc.getWorld().getName() + "\n").color(ChatColor.YELLOW)
                        .append("Click to copy coordinates", ComponentBuilder.FormatRetention.NONE).color(ChatColor.GRAY)
                        .create()));
        comp.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, coords));
        return comp;
    }

    private String hoverTextForItem(ItemStack item) {
        StringBuilder sb = new StringBuilder();
        sb.append(itemName(item));
        if (item.getAmount() > 1) {
            sb.append(" x").append(item.getAmount());
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasLore() && meta.getLore() != null) {
            for (String line : meta.getLore()) {
                sb.append("\n").append(ChatColor.GRAY).append(line);
            }
        }
        if (meta != null && meta.hasEnchants()) {
            meta.getEnchants().forEach((ench, level) ->
                    sb.append("\n").append(ChatColor.BLUE)
                            .append(ench.getKey().getKey()).append(" ").append(level));
        }
        return sb.toString();
    }

    private String itemName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return ChatColor.stripColor(meta.getDisplayName());
        }
        String raw = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return capitalize(raw);
    }

    private String capitalize(String s) {
        String[] parts = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    /** 41 slots: 0-35 main storage, 36-39 armor (boots,leggings,chest,helmet), 40 offhand */
    private ItemStack[] fullInventorySnapshot(Player player) {
        List<ItemStack> list = new ArrayList<>();
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (ItemStack it : storage) list.add(it);
        list.add(player.getInventory().getBoots());
        list.add(player.getInventory().getLeggings());
        list.add(player.getInventory().getChestplate());
        list.add(player.getInventory().getHelmet());
        list.add(player.getInventory().getItemInOffHand());
        return list.toArray(new ItemStack[0]);
    }
}
