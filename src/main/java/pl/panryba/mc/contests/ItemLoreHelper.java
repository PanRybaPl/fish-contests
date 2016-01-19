/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.panryba.mc.contests;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 *
 * @author PanRyba.pl
 */
public class ItemLoreHelper {
    public static void addItemLore(ItemStack item, String[] loreStrings) {
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null) {
                lore = new ArrayList<>();
            }

            for(String loreString : loreStrings) {
                lore.add(loreString);
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }        
}
