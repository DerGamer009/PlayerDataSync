package com.example.playerdatasync.premium.managers;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.example.playerdatasync.premium.core.PlayerDataSyncPremium;

/**
 * Handles advancement synchronization in a staged manner so we can
 * import large advancement sets without blocking the main server thread.
 */
public class AdvancementSyncManager implements Listener {
    private final PlayerDataSyncPremium plugin;
    private final Map<UUID, PlayerAdvancementState> states = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<NamespacedKey> cachedAdvancements = new CopyOnWriteArrayList<>();

    private volatile boolean globalImportRunning = false;
    private volatile boolean globalImportCompleted = false;
    private BukkitTask globalImportTask;

    public AdvancementSyncManager(PlayerDataSyncPremium plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        if (plugin.getConfig().getBoolean("performance.preload_advancements_on_startup", true)) {
            // Delay by one tick so Bukkit finished loading advancements.
            SchedulerUtils.runTask(plugin, player, () -> startGlobalImport(false));
        }
    }

    public void shutdown() {
        if (globalImportTask != null) {
            globalImportTask.cancel();
            globalImportTask = null;
        }
    }

    public void reloadFromConfig() {
        if (plugin.getConfig().getBoolean("performance.preload_advancements_on_startup", true)) {
            if (!globalImportCompleted && !globalImportRunning) {
                startGlobalImport(false);
            }
        }
    }

    @EventHandler
    public void onAdvancementCompleted(PlayerAdvancementDoneEvent event) {
        Advancement advancement = event.getAdvancement();
        if (advancement == null) {
            return;
        }
        recordAdvancement(event.getPlayer().getUniqueId(), advancement.getKey().toString());
    }

    public void recordAdvancement(UUID uuid, String key) {
        PlayerAdvancementState state = states.computeIfAbsent(uuid, id -> new PlayerAdvancementState());
        state.completedAdvancements.add(key);
        if (state.importInProgress) {
            state.pendingDuringImport.add(key);
        }
        state.lastUpdated = System.currentTimeMillis();
    }

    public void handlePlayerJoin(Player player) {
        if (!plugin.getConfig().getBoolean("performance.automatic_player_advancement_import", true)) {
            return;
        }

        PlayerAdvancementState state = states.computeIfAbsent(player.getUniqueId(), key -> new PlayerAdvancementState());
        if (state.importFinished || state.importInProgress) {
            return;
        }

        queuePlayerImport(player, false);
    }

    public void handlePlayerQuit(Player player) {
        PlayerAdvancementState state = states.get(player.getUniqueId());
        if (state != null) {
            state.importInProgress = false;
        }
    }

    public void seedFromDatabase(UUID uuid, String csv) {
        PlayerAdvancementState state = states.computeIfAbsent(uuid, key -> new PlayerAdvancementState());
        state.completedAdvancements.clear();
        state.pendingDuringImport.clear();

        if (csv == null) {
            state.importFinished = false;
            state.lastUpdated = System.currentTimeMillis();
            return;
        }

        if (!csv.isEmpty()) {
            String[] parts = csv.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    state.completedAdvancements.add(trimmed);
                }
            }
        }

        state.importFinished = true;
        state.lastUpdated = System.currentTimeMillis();
    }

    public void forgetPlayer(UUID uuid) {
        states.remove(uuid);
    }

    public String serializeForSave(Player player) {
        PlayerAdvancementState state = states.computeIfAbsent(player.getUniqueId(), key -> new PlayerAdvancementState());

        if (!state.importFinished && !state.importInProgress) {
            if (plugin.getConfig().getBoolean("performance.automatic_player_advancement_import", true)) {
                queuePlayerImport(player, false);
            }
        }

        if (state.completedAdvancements.isEmpty()) {
            return "";
        }

        return state.completedAdvancements.stream()
            .sorted()
            .collect(Collectors.joining(","));
    }

    public void queuePlayerImport(Player player, boolean force) {
        PlayerAdvancementState state = states.computeIfAbsent(player.getUniqueId(), key -> new PlayerAdvancementState());
        if (!force) {
            if (state.importInProgress || state.importFinished) {
                return;
            }
        } else if (state.importInProgress) {
            return;
        }

        List<NamespacedKey> keys = getCachedAdvancementKeys();
        if (keys.isEmpty()) {
            if (globalImportRunning || !globalImportCompleted) {
                if (!state.awaitingGlobalCache) {
                    state.importInProgress = true;
                    state.awaitingGlobalCache = true;
                    SchedulerUtils.runTaskLater(plugin, player, () -> {
                        state.importInProgress = false;
                        state.awaitingGlobalCache = false;
                        if (player.isOnline()) {
                            queuePlayerImport(player, force);
                        }
                    }, 20L);
                }
                return;
            }

            // No advancements available at all (unlikely but possible if server has none)
            state.importFinished = true;
            state.importInProgress = false;
            return;
        }

        final int batchSize = Math.max(1,
            plugin.getConfig().getInt("performance.player_advancement_import_batch_size", 150));

        state.importInProgress = true;
        state.pendingDuringImport.clear();
        state.lastImportStart = System.currentTimeMillis();

        Iterator<NamespacedKey> iterator = keys.iterator();
        Set<String> imported = new HashSet<>();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    state.importInProgress = false;
                    cancel();
                    return;
                }

                int processed = 0;
                while (iterator.hasNext() && processed < batchSize) {
                    processed++;
                    NamespacedKey key = iterator.next();
                    Advancement advancement = Bukkit.getAdvancement(key);
                    if (advancement == null) {
                        continue;
                    }

                    AdvancementProgress progress = player.getAdvancementProgress(advancement);
                    if (progress != null && progress.isDone()) {
                        imported.add(key.toString());
                    }
                }

                if (!iterator.hasNext()) {
                    state.completedAdvancements.clear();
                    state.completedAdvancements.addAll(imported);
                    if (!state.pendingDuringImport.isEmpty()) {
                        state.completedAdvancements.addAll(state.pendingDuringImport);
                        state.pendingDuringImport.clear();
                    }

                    state.importFinished = true;
                    state.importInProgress = false;
                    state.lastImportDuration = System.currentTimeMillis() - state.lastImportStart;
                    state.lastUpdated = System.currentTimeMillis();

                    if (plugin.isPerformanceLoggingEnabled()) {
                        plugin.getLogger().info("Imported " + state.completedAdvancements.size() +
                            " achievements for " + player.getName() + " in " + state.lastImportDuration + "ms");
                    }

                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public boolean startGlobalImport(boolean force) {
        if (globalImportRunning) {
            return false;
        }
        if (globalImportCompleted && !force) {
            return false;
        }

        Iterator<Advancement> iterator = Bukkit.getServer().advancementIterator();
        cachedAdvancements.clear();
        globalImportRunning = true;
        globalImportCompleted = false;

        final int batchSize = Math.max(1,
            plugin.getConfig().getInt("performance.advancement_import_batch_size", 250));

        globalImportTask = new BukkitRunnable() {
            @Override
            public void run() {
                int processed = 0;
                while (iterator.hasNext() && processed < batchSize) {
                    processed++;
                    Advancement advancement = iterator.next();
                    if (advancement != null) {
                        cachedAdvancements.add(advancement.getKey());
                    }
                }

                if (!iterator.hasNext()) {
                    globalImportRunning = false;
                    globalImportCompleted = true;
                    if (plugin.isPerformanceLoggingEnabled()) {
                        plugin.getLogger().info("Cached " + cachedAdvancements.size() +
                            " advancement definitions for staged synchronization");
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return true;
    }

    public List<NamespacedKey> getCachedAdvancementKeys() {
        if (cachedAdvancements.isEmpty() && !globalImportRunning) {
            startGlobalImport(false);
        }
        return new ArrayList<>(cachedAdvancements);
    }

    public String getGlobalImportStatus() {
        if (globalImportRunning) {
            return "running (" + cachedAdvancements.size() + " cached so far)";
        }
        if (globalImportCompleted) {
            return "ready (" + cachedAdvancements.size() + " cached)";
        }
        return "not started";
    }

    public String getPlayerStatus(UUID uuid) {
        PlayerAdvancementState state = states.get(uuid);
        if (state == null) {
            return "no data";
        }
        if (state.importInProgress) {
            return "importing (" + state.completedAdvancements.size() + " known so far)";
        }
        if (state.importFinished) {
            return "ready (" + state.completedAdvancements.size() + " cached)";
        }
        return "pending import";
    }

    public void forceRescan(Player player) {
        queuePlayerImport(player, true);
    }

    private static class PlayerAdvancementState {
        private final Set<String> completedAdvancements = ConcurrentHashMap.newKeySet();
        private final Set<String> pendingDuringImport = ConcurrentHashMap.newKeySet();
        private volatile boolean importFinished = false;
        private volatile boolean importInProgress = false;
        private volatile boolean awaitingGlobalCache = false;
        private volatile long lastImportStart = 0;
        private volatile long lastImportDuration = 0;
        // Field reserved for future use (tracking last update time)
        @SuppressWarnings("unused")
        private volatile long lastUpdated = 0;
    }
}
