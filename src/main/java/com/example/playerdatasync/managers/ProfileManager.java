package com.example.playerdatasync.managers;

import com.example.playerdatasync.core.PlayerDataSync;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ProfileManager {

    private final PlayerDataSync plugin;
    private final Map<String, ProfileData> stats = new ConcurrentHashMap<>();

    public ProfileManager(PlayerDataSync plugin) {
        this.plugin = plugin;
    }

    public void record(String operation, long durationMs) {
        stats.computeIfAbsent(operation, k -> new ProfileData()).add(durationMs);
    }

    public void showProfile(CommandSender sender) {
        sender.sendMessage("§8§m----------§r §6Performance Profile §8§m----------");
        if (stats.isEmpty()) {
            sender.sendMessage("§7No data recorded yet.");
        } else {
            stats.forEach((op, data) -> {
                sender.sendMessage(String.format("§e%s: §fAvg: §b%dms §f| Max: §c%dms §f| Count: §7%d",
                        op, data.getAverage(), data.getMax(), data.getCount()));
            });
        }
        sender.sendMessage("§8§m----------------------------------------");
    }

    public void reset() {
        stats.clear();
    }

    private static class ProfileData {
        private final AtomicLong totalMs = new AtomicLong(0);
        private final AtomicLong maxMs = new AtomicLong(0);
        private final AtomicLong count = new AtomicLong(0);

        public void add(long ms) {
            totalMs.addAndGet(ms);
            count.incrementAndGet();
            long currentMax;
            while (ms > (currentMax = maxMs.get())) {
                if (maxMs.compareAndSet(currentMax, ms)) break;
            }
        }

        public long getAverage() {
            long c = count.get();
            return c == 0 ? 0 : totalMs.get() / c;
        }

        public long getMax() {
            return maxMs.get();
        }

        public long getCount() {
            return count.get();
        }
    }
}
