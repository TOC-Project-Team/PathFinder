package org.momu.pathfinder.navigation.display;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.momu.pathfinder.config.Messages;
import org.momu.pathfinder.navigation.session.NavigationTracker;

/**
 * The action bar line shown while navigating: target name, distance, direction relative to where the player
 * is looking, and height difference.
 */
public final class ActionBarHud {
    /** Direction names for each 45° sector, starting straight ahead and going clockwise. */
    private static final String[] DIRECTION_KEYS = {
            "messages.front", "messages.front-right", "messages.right", "messages.right-rear",
            "messages.back", "messages.left-rear", "messages.left", "messages.left-front"
    };

    private ActionBarHud() {
    }

    @SuppressWarnings("deprecation")
    public static void show(Player player, Location target, String targetName) {
        Location from = player.getLocation().clone();
        double distance = from.distance(target);
        double horizontalDistance = Math.sqrt(Math.pow(from.getX() - target.getX(), 2.0)
                + Math.pow(from.getZ() - target.getZ(), 2.0));
        double verticalDistance = target.getY() - from.getY();
        Vector direction = target.toVector().subtract(from.toVector());
        double angle = Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        double relativeAngle = (angle - from.getYaw() + 360.0) % 360.0;
        String directionName = directionName(player, relativeAngle);
        String verticalDirection = Messages.get(player, verticalDistance > 0.0 ? "messages.up" : "messages.down");

        if (NavigationTracker.getInstance().isActionBarSuppressed(player.getUniqueId())) {
            return;
        }
        String message = Messages.get(player, "messages.action-bar",
                targetName, String.format("%.1f", distance), directionName, (int) relativeAngle + "°",
                String.format("%.1f", horizontalDistance), String.format("%.1f", Math.abs(verticalDistance)),
                verticalDirection);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    /** Names the 45° sector of {@code angle} (degrees clockwise from straight ahead). */
    public static String directionName(Player player, double angle) {
        double normalized = (angle % 360.0 + 360.0) % 360.0;
        int sector = (int) Math.floor((normalized + 22.5) / 45.0) % DIRECTION_KEYS.length;
        return Messages.get(player, DIRECTION_KEYS[sector]);
    }
}
