package org.momu.pathfinder.testsupport;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * A fake {@link Player} that records every particle it is asked to spawn.
 */
public final class ParticleRecorder {
    private final StringBuilder log = new StringBuilder();
    private final Player player;

    public ParticleRecorder(FakeWorld world, Location location) {
        player = Proxies.fake(Player.class, (self, method, args) -> switch (method) {
            case "spawnParticle" -> {
                record(args);
                yield null;
            }
            case "getLocation" -> location.clone();
            case "getWorld" -> world.world();
            case "getName" -> "tester";
            default -> null;
        });
    }

    public Player player() {
        return player;
    }

    public String log() {
        return log.toString();
    }

    private void record(Object[] args) {
        Particle particle = (Particle) args[0];
        Location at = (Location) args[1];
        log.append(String.format(Locale.ROOT, "  %s %.4f %.4f %.4f n=%s", particle, at.getX(), at.getY(), at.getZ(),
                args[2]));
        Object data = args[args.length - 1];
        if (data instanceof Particle.DustOptions dust) {
            log.append(String.format(Locale.ROOT, " rgb=%06x size=%.3f", dust.getColor().asRGB(), dust.getSize()));
        }
        log.append('\n');
    }
}
