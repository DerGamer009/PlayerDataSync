package com.example.playerdatasync.nms;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import java.util.UUID;

public class FallbackNMSHandler implements NMSHandler {
    @Override
    public void setItemInOffHand(Player player, ItemStack item) {
        try {
            player.getInventory().getClass().getMethod("setItemInOffHand", ItemStack.class).invoke(player.getInventory(), item);
        } catch (Exception ignored) {
            // Probably version < 1.9
        }
    }

    @Override
    public ItemStack getItemInOffHand(Player player) {
        try {
            return (ItemStack) player.getInventory().getClass().getMethod("getItemInOffHand").invoke(player.getInventory());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public double getGenericMaxHealth(Player player) {
        try {
            // Try to use Attributes API first (1.9+)
            Object attribute = player.getClass().getMethod("getAttribute", Class.forName("org.bukkit.attribute.Attribute")).invoke(player, 
                Class.forName("org.bukkit.attribute.Attribute").getField("GENERIC_MAX_HEALTH").get(null));
            if (attribute != null) {
                return (double) attribute.getClass().getMethod("getValue").invoke(attribute);
            }
        } catch (Exception ignored) {}
        
        // Fallback to deprecated method for 1.8
        return player.getMaxHealth();
    }

    @Override
    public void setGenericMaxHealth(Player player, double value) {
        try {
            // Try to use Attributes API first (1.9+)
            Object attribute = player.getClass().getMethod("getAttribute", Class.forName("org.bukkit.attribute.Attribute")).invoke(player, 
                Class.forName("org.bukkit.attribute.Attribute").getField("GENERIC_MAX_HEALTH").get(null));
            if (attribute != null) {
                attribute.getClass().getMethod("setBaseValue", double.class).invoke(attribute, value);
                return;
            }
        } catch (Exception ignored) {}
        
        // Fallback to deprecated method for 1.8
        player.setMaxHealth(value);
    }

    @Override
    public String serializeAttributes(Player player) {
        try {
            Class<?> attrClass = Class.forName("org.bukkit.attribute.Attribute");
            Object[] values = (Object[]) attrClass.getMethod("values").invoke(null);
            java.util.StringJoiner joiner = new java.util.StringJoiner("|");
            
            for (Object attr : values) {
                Object instance = player.getClass().getMethod("getAttribute", attrClass).invoke(player, attr);
                if (instance != null) {
                    double baseValue = (double) instance.getClass().getMethod("getBaseValue").invoke(instance);
                    String name = (String) attrClass.getMethod("name").invoke(attr);
                    joiner.add(name + ":" + baseValue);
                }
            }
            return joiner.toString();
        } catch (Exception e) {
            // 1.8 Fallback
            java.util.StringJoiner joiner = new java.util.StringJoiner("|");
            joiner.add("GENERIC_MAX_HEALTH:" + player.getMaxHealth());
            joiner.add("GENERIC_MOVEMENT_SPEED:" + player.getWalkSpeed());
            joiner.add("GENERIC_FLYING_SPEED:" + player.getFlySpeed());
            return joiner.toString();
        }
    }

    @Override
    public void loadAttributes(Player player, String data) {
        if (data == null || data.isEmpty()) return;
        try {
            Class<?> attrClass = Class.forName("org.bukkit.attribute.Attribute");
            String[] parts = data.split("\\|");
            for (String part : parts) {
                String[] kv = part.split(":");
                if (kv.length == 2) {
                    try {
                        Object attr = attrClass.getMethod("valueOf", String.class).invoke(null, kv[0]);
                        double value = Double.parseDouble(kv[1]);
                        Object instance = player.getClass().getMethod("getAttribute", attrClass).invoke(player, attr);
                        if (instance != null) {
                            instance.getClass().getMethod("setBaseValue", double.class).invoke(instance, value);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            // 1.8 Fallback
            String[] parts = data.split("\\|");
            for (String part : parts) {
                String[] kv = part.split(":");
                if (kv.length == 2) {
                    try {
                        String name = kv[0];
                        double value = Double.parseDouble(kv[1]);
                        if (name.equals("GENERIC_MAX_HEALTH")) {
                            player.setMaxHealth(value);
                        } else if (name.equals("GENERIC_MOVEMENT_SPEED")) {
                            player.setWalkSpeed((float) value);
                        } else if (name.equals("GENERIC_FLYING_SPEED")) {
                            player.setFlySpeed((float) value);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    @Override public void setupAdvancements(Plugin plugin) {}
    @Override public void shutdownAdvancements() {}
    @Override public void handlePlayerJoinAdvancements(Player player) {}
    @Override public void handlePlayerQuitAdvancements(Player player) {}
    @Override public void seedAdvancementsFromDatabase(UUID uuid, String csv) {}

    @Override
    public String serializeAdvancements(Player player) {
        try {
            Class<?> advClass = Class.forName("org.bukkit.advancement.Advancement");
            java.util.Iterator<?> it = (java.util.Iterator<?>) player.getServer().getClass().getMethod("advancementIterator").invoke(player.getServer());
            java.util.StringJoiner joiner = new java.util.StringJoiner(",");
            
            while (it.hasNext()) {
                Object adv = it.next();
                Object prog = player.getClass().getMethod("getAdvancementProgress", advClass).invoke(player, adv);
                boolean isDone = (boolean) prog.getClass().getMethod("isDone").invoke(prog);
                if (isDone) {
                    Object key = adv.getClass().getMethod("getKey").invoke(adv);
                    joiner.add(key.toString());
                }
            }
            return joiner.toString();
        } catch (Exception e) {
            // 1.8-1.11 Fallback for Achievements
            try {
                Class<?> achClass = Class.forName("org.bukkit.Achievement");
                Object[] values = (Object[]) achClass.getMethod("values").invoke(null);
                java.util.StringJoiner joiner = new java.util.StringJoiner(",");
                for (Object ach : values) {
                    boolean has = (boolean) player.getClass().getMethod("hasAchievement", achClass).invoke(player, ach);
                    if (has) {
                        joiner.add(ach.toString());
                    }
                }
                return joiner.toString();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    @Override
    public void loadAdvancements(Player player, String data) {
        if (data == null || data.isEmpty()) return;
        try {
            Class<?> nskClass = Class.forName("org.bukkit.NamespacedKey");
            Class<?> advClass = Class.forName("org.bukkit.advancement.Advancement");
            String[] keys = data.split(",");
            for (String key : keys) {
                try {
                    String[] parts = key.split(":");
                    if (parts.length == 2) {
                        Object nsk = null;
                        try {
                            nsk = nskClass.getConstructor(String.class, String.class).newInstance(parts[0], parts[1]);
                        } catch (Exception e) {
                            try {
                                nsk = nskClass.getMethod("minecraft", String.class).invoke(null, parts[1]);
                            } catch (Exception e2) {
                                continue;
                            }
                        }
                        
                        Object adv = player.getServer().getClass().getMethod("getAdvancement", nskClass).invoke(player.getServer(), nsk);
                        if (adv != null) {
                            Object prog = player.getClass().getMethod("getAdvancementProgress", advClass).invoke(player, adv);
                            java.util.Collection<String> remaining = (java.util.Collection<String>) prog.getClass().getMethod("getRemainingCriteria").invoke(prog);
                            for (String criterion : remaining) {
                                prog.getClass().getMethod("awardCriteria", String.class).invoke(prog, criterion);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            // 1.8-1.11 Fallback for Achievements
            try {
                Class<?> achClass = Class.forName("org.bukkit.Achievement");
                String[] parts = data.split(",");
                for (String part : parts) {
                    if (part.isEmpty()) continue;
                    try {
                        Object ach = achClass.getMethod("valueOf", String.class).invoke(null, part.trim().toUpperCase());
                        player.getClass().getMethod("awardAchievement", achClass).invoke(player, ach);
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
    }

    @Override public void queueAdvancementImport(Player player, boolean force) {}
}
