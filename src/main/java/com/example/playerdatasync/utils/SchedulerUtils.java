package com.example.playerdatasync.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Utility class for Folia compatibility
 * Automatically detects Folia and uses appropriate schedulers
 * 
 * Based on official Folia API from PaperMC:
 * - GlobalRegionScheduler: For global tasks
 * - RegionScheduler: For region-specific tasks (player/location-based)
 * - AsyncScheduler: For async tasks
 * 
 * @see <a href="https://papermc.io/downloads/folia">Folia Downloads</a>
 * @see <a href="https://docs.papermc.io/paper/dev/folia-support">Folia Support Documentation</a>
 */
public class SchedulerUtils {
    private static final boolean IS_FOLIA;
    
    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            // Not Folia
        }
        IS_FOLIA = folia;
    }
    
    /**
     * Check if the server is running Folia
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }
    
    /**
     * Run a task synchronously on the main thread (or region for Folia)
     * Uses GlobalRegionScheduler on Folia for global tasks
     */
    public static BukkitTask runTask(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            try {
                // Use GlobalRegionScheduler for global tasks (Folia API)
                Object server = Bukkit.getServer();
                Object globalScheduler = server.getClass()
                    .getMethod("getGlobalRegionScheduler")
                    .invoke(server);
                
                // GlobalRegionScheduler.run(Plugin, Consumer)
                return (BukkitTask) globalScheduler.getClass()
                    .getMethod("run", Plugin.class, Consumer.class)
                    .invoke(globalScheduler, plugin, (Consumer<Object>) (t) -> task.run());
            } catch (Exception e) {
                // Fallback to regular scheduler if reflection fails
                plugin.getLogger().warning("Failed to use Folia GlobalRegionScheduler, falling back to Bukkit scheduler: " + e.getMessage());
                return Bukkit.getScheduler().runTask(plugin, task);
            }
        }
        return Bukkit.getScheduler().runTask(plugin, task);
    }
    
    /**
     * Run a task synchronously for a specific player (uses region scheduler on Folia)
     * Uses RegionScheduler on Folia for player-specific tasks
     */
    public static BukkitTask runTask(Plugin plugin, Player player, Runnable task) {
        if (IS_FOLIA) {
            try {
                Location loc = player.getLocation();
                Object server = Bukkit.getServer();
                Object regionScheduler = server.getClass()
                    .getMethod("getRegionScheduler")
                    .invoke(server);
                
                // RegionScheduler.run(Plugin, Location, Consumer)
                return (BukkitTask) regionScheduler.getClass()
                    .getMethod("run", Plugin.class, Location.class, Consumer.class)
                    .invoke(regionScheduler, plugin, loc, (Consumer<Object>) (t) -> task.run());
            } catch (Exception e) {
                // Fallback to regular scheduler if reflection fails
                plugin.getLogger().warning("Failed to use Folia RegionScheduler, falling back to Bukkit scheduler: " + e.getMessage());
                return Bukkit.getScheduler().runTask(plugin, task);
            }
        }
        return Bukkit.getScheduler().runTask(plugin, task);
    }
    
    /**
     * Run a task synchronously for a specific location (uses region scheduler on Folia)
     */
    public static BukkitTask runTask(Plugin plugin, Location location, Runnable task) {
        if (IS_FOLIA) {
            try {
                Object regionScheduler = Bukkit.getServer().getClass()
                    .getMethod("getRegionScheduler")
                    .invoke(Bukkit.getServer());
                
                return (BukkitTask) regionScheduler.getClass()
                    .getMethod("run", Plugin.class, Location.class, Consumer.class)
                    .invoke(regionScheduler, plugin, location, (Consumer<Object>) (t) -> task.run());
            } catch (Exception e) {
                // Fallback to regular scheduler if reflection fails
                return Bukkit.getScheduler().runTask(plugin, task);
            }
        }
        return Bukkit.getScheduler().runTask(plugin, task);
    }
    
    /**
     * Run a task asynchronously
     * Uses AsyncScheduler on Folia for async tasks
     */
    public static BukkitTask runTaskAsync(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            try {
                Object server = Bukkit.getServer();
                Object asyncScheduler = server.getClass()
                    .getMethod("getAsyncScheduler")
                    .invoke(server);
                
                // AsyncScheduler.runNow(Plugin, Consumer)
                return (BukkitTask) asyncScheduler.getClass()
                    .getMethod("runNow", Plugin.class, Consumer.class)
                    .invoke(asyncScheduler, plugin, (Consumer<Object>) (t) -> task.run());
            } catch (Exception e) {
                // Fallback to regular scheduler if reflection fails
                plugin.getLogger().warning("Failed to use Folia AsyncScheduler, falling back to Bukkit scheduler: " + e.getMessage());
                return Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            }
        }
        return Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }
    
    /**
     * Run a task later synchronously
     */
    public static BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay) {
        if (IS_FOLIA) {
            try {
                Object globalScheduler = Bukkit.getServer().getClass()
                    .getMethod("getGlobalRegionScheduler")
                    .invoke(Bukkit.getServer());
                
                return (BukkitTask) globalScheduler.getClass()
                    .getMethod("runDelayed", Plugin.class, Consumer.class, long.class)
                    .invoke(globalScheduler, plugin, (Consumer<Object>) (t) -> task.run(), delay);
            } catch (Exception e) {
                // Fallback to regular scheduler if reflection fails
                return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
            }
        }
        return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }
    
    /**
     * Run a task later synchronously for a specific player
     */
    public static BukkitTask runTaskLater(Plugin plugin, Player player, Runnable task, long delay) {
        if (IS_FOLIA) {
            try {
                Location loc = player.getLocation();
                Object regionScheduler = Bukkit.getServer().getClass()
                    .getMethod("getRegionScheduler")
                    .invoke(Bukkit.getServer());
                
                return (BukkitTask) regionScheduler.getClass()
                    .getMethod("runDelayed", Plugin.class, Location.class, Consumer.class, long.class)
                    .invoke(regionScheduler, plugin, loc, (Consumer<Object>) (t) -> task.run(), delay);
            } catch (Exception e) {
                // Fallback to regular scheduler if reflection fails
                return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
            }
        }
        return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }
    
    /**
     * Run a task later asynchronously
     */
    public static BukkitTask runTaskLaterAsync(Plugin plugin, Runnable task, long delay) {
        if (IS_FOLIA) {
            try {
                Object asyncScheduler = Bukkit.getServer().getClass()
                    .getMethod("getAsyncScheduler")
                    .invoke(Bukkit.getServer());
                
                // Folia uses milliseconds for async scheduler
                long delayMs = delay * 50; // Convert ticks to milliseconds
                return (BukkitTask) asyncScheduler.getClass()
                    .getMethod("runDelayed", Plugin.class, Consumer.class, long.class, TimeUnit.class)
                    .invoke(asyncScheduler, plugin, (Consumer<Object>) (t) -> task.run(), delayMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                // Fallback to regular scheduler if reflection fails
                return Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
            }
        }
        return Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
    }
    
    /**
     * Run a repeating task synchronously
     */
    public static BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
        if (IS_FOLIA) {
            try {
                Object globalScheduler = Bukkit.getServer().getClass()
                    .getMethod("getGlobalRegionScheduler")
                    .invoke(Bukkit.getServer());
                
                return (BukkitTask) globalScheduler.getClass()
                    .getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class)
                    .invoke(globalScheduler, plugin, (Consumer<Object>) (t) -> task.run(), delay, period);
            } catch (Exception e) {
                // Fallback to regular scheduler if reflection fails
                return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
            }
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
    }
    
    /**
     * Run a repeating task asynchronously
     */
    public static BukkitTask runTaskTimerAsync(Plugin plugin, Runnable task, long delay, long period) {
        if (IS_FOLIA) {
            try {
                Object asyncScheduler = Bukkit.getServer().getClass()
                    .getMethod("getAsyncScheduler")
                    .invoke(Bukkit.getServer());
                
                // Folia uses milliseconds for async scheduler
                long delayMs = delay * 50; // Convert ticks to milliseconds
                long periodMs = period * 50; // Convert ticks to milliseconds
                return (BukkitTask) asyncScheduler.getClass()
                    .getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class)
                    .invoke(asyncScheduler, plugin, (Consumer<Object>) (t) -> task.run(), delayMs, periodMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                // Fallback to regular scheduler if reflection fails
                return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
            }
        }
        return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
    }
    
    /**
     * Check if current thread is the main thread (or region thread for Folia)
     */
    public static boolean isPrimaryThread() {
        if (IS_FOLIA) {
            try {
                // On Folia, check if we're on a region thread
                return Bukkit.isPrimaryThread();
            } catch (Exception e) {
                return Bukkit.isPrimaryThread();
            }
        }
        return Bukkit.isPrimaryThread();
    }
    
    /**
     * Call a method synchronously (for Folia compatibility)
     */
    public static <T> T callSyncMethod(Plugin plugin, java.util.concurrent.Callable<T> callable) throws Exception {
        if (IS_FOLIA) {
            // On Folia, we need to use a different approach
            // For now, we'll use a CompletableFuture
            java.util.concurrent.CompletableFuture<T> future = new java.util.concurrent.CompletableFuture<>();
            runTask(plugin, () -> {
                try {
                    future.complete(callable.call());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future.get();
        }
        return Bukkit.getScheduler().callSyncMethod(plugin, callable).get();
    }
}
