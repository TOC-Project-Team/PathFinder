package org.momu.pathfinder.command.nav;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Splits long chat lists into pages with clickable previous/next links.
 */
final class ChatPages {
    static final int PAGE_SIZE = 10;

    private ChatPages() {
    }

    static int pageCount(int itemCount) {
        return (int) Math.ceil(itemCount / (double) PAGE_SIZE);
    }

    /** Clamps a 1-based page number to the available pages. */
    static int clamp(int page, int itemCount) {
        return Math.min(page, pageCount(itemCount));
    }

    static <T> List<T> slice(List<T> items, int page) {
        int from = Math.max(0, (page - 1) * PAGE_SIZE);
        int to = Math.min(items.size(), from + PAGE_SIZE);
        return items.subList(from, to);
    }

    /** For players, adds "« | »" links that run {@code baseCommand --page=N}. */
    @SuppressWarnings("deprecation")
    static void sendNavigation(CommandSender sender, String baseCommand, int page, int totalPages) {
        if (!(sender instanceof Player player) || totalPages <= 1) {
            return;
        }
        player.spigot().sendMessage(TextComponent.fromLegacyText("§7« "));
        TextComponent previous = new TextComponent("§b« ");
        previous.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                baseCommand + " --page=" + Math.max(1, page - 1)));
        TextComponent separator = new TextComponent("§7| ");
        TextComponent next = new TextComponent("§b»");
        next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                baseCommand + " --page=" + Math.min(totalPages, page + 1)));
        player.spigot().sendMessage(previous, separator, next);
    }
}
