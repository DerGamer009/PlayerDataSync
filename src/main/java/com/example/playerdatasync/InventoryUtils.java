package com.example.playerdatasync;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class InventoryUtils {
    public static String itemStackArrayToBase64(ItemStack[] items) throws IOException {
        if (items == null) return "";
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
        }
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    public static ItemStack[] itemStackArrayFromBase64(String data) throws IOException, ClassNotFoundException {
        if (data == null || data.isEmpty()) return new ItemStack[0];
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        ItemStack[] items;
        try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            int length = dataInput.readInt();
            items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
        }
        return items;
    }
}
