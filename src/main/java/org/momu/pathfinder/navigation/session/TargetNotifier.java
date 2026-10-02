package org.momu.pathfinder.navigation.session;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.momu.pathfinder.config.Messages;
import org.momu.pathfinder.navigation.runtime.Scheduling;

/**
 * Tells a player that someone started or stopped navigating to them, with a chat message and a boss bar
 * that disappears after five seconds.
 */
final class TargetNotifier {
    private static final long BOSS_BAR_TICKS = 100L;

    private TargetNotifier() {
    }

    static void navigationStarted(Player target, Player navigator) {
        show(target, "messages.navigating-player-to-you", navigator, BarColor.BLUE);
    }

    static void navigationStopped(Player target, Player navigator) {
        show(target, "messages.navigating-player-to-you-cancelled", navigator, BarColor.RED);
    }

    private static void show(Player target, String key, Player navigator, BarColor color) {
        String text = Messages.get(target, key, navigator.getName());
        BossBar bossBar = Bukkit.createBossBar(text, color, BarStyle.SOLID);
        bossBar.setProgress(1.0);
        bossBar.addPlayer(target);
        bossBar.setVisible(true);
        target.sendMessage("§a" + text);
        if (Scheduling.runGlobalLater(bossBar::removeAll, BOSS_BAR_TICKS) == null) {
            bossBar.removeAll();
        }
    }
}
