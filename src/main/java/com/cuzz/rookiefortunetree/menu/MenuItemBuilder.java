package com.cuzz.rookiefortunetree.menu;

import com.cuzz.rookiefortunetree.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class MenuItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;

    private MenuItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, Math.max(1, amount));
        this.meta = item.getItemMeta();
    }

    public static MenuItemBuilder of(Material material) {
        return new MenuItemBuilder(material, 1);
    }

    public MenuItemBuilder displayName(String name) {
        if (meta != null && name != null) {
            meta.setDisplayName(TextUtil.colorize(name));
        }
        return this;
    }

    public MenuItemBuilder lore(List<String> loreLines) {
        if (meta != null && loreLines != null) {
            List<String> colored = new ArrayList<>();
            for (String line : loreLines) {
                colored.add(TextUtil.colorize(line));
            }
            meta.setLore(colored);
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }
}

