package org.momu.pathfinder.golden;

import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.momu.pathfinder.testsupport.FakeWorld;
import org.momu.pathfinder.testsupport.GoldenFile;
import org.momu.pathfinder.testsupport.ParticleRecorder;
import org.momu.pathfinder.testsupport.Scenarios;
import org.momu.pathfinder.testsupport.Scenarios.Scenario;

import java.io.IOException;
import java.util.Locale;

/**
 * Golden-master tests that pin the observable behavior of the navigation code: the paths it finds,
 * how it picks landing spots near water, and the exact particles it draws.
 */
class NavigationGoldenTest {

    @BeforeEach
    void setUp() {
        NavigationProbe.applyDefaultSettings();
    }

    @Test
    void pathsMatchGolden() throws IOException {
        StringBuilder out = new StringBuilder();
        for (Scenario scenario : Scenarios.all()) {
            out.append(scenario.name()).append('\n');
            out.append(NavigationProbe.describePath(scenario.start(), scenario.end()));
        }
        GoldenFile.assertMatches("paths.txt", out.toString());
    }

    @Test
    void landingSearchMatchesGolden() throws IOException {
        StringBuilder out = new StringBuilder();
        for (Scenario scenario : Scenarios.all()) {
            FakeWorld world = scenario.world();
            out.append(scenario.name()).append('\n');
            for (int x = 0; x <= 20; x += 4) {
                for (int z = -4; z <= 8; z += 4) {
                    for (int y = Scenarios.GROUND_Y - 3; y <= Scenarios.GROUND_Y + 3; y += 2) {
                        Location probe = world.loc(x, y, z);
                        out.append("  ").append(x).append(',').append(y).append(',').append(z)
                                .append(" safe=").append(NavigationProbe.isSafeLanding(probe) ? 'Y' : 'N');
                        if (world.get(x, y, z) == Material.WATER) {
                            out.append(" near=").append(format(NavigationProbe.findSafeLandingNearWater(probe)))
                                    .append(" adjusted=").append(format(NavigationProbe.adjustTargetForWater(probe)));
                        }
                        out.append('\n');
                    }
                }
            }
        }
        GoldenFile.assertMatches("landing.txt", out.toString());
    }

    @Test
    void renderedParticlesMatchGolden() throws IOException {
        StringBuilder out = new StringBuilder();
        for (Scenario scenario : Scenarios.all()) {
            ParticleRecorder recorder = new ParticleRecorder(scenario.world(), scenario.start());
            NavigationProbe.renderPath(recorder.player(), scenario.start(), scenario.end());
            out.append(scenario.name()).append('\n').append(recorder.log());
        }
        GoldenFile.assertMatches("render.txt", out.toString());
    }

    private static String format(Location location) {
        if (location == null) {
            return "null";
        }
        return String.format(Locale.ROOT, "%.2f/%.2f/%.2f", location.getX(), location.getY(), location.getZ());
    }
}
