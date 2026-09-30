package org.momu.pathfinder.testsupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Compares output against a checked-in golden file. Run the tests with {@code -Dgolden.update=true}
 * to rewrite the golden files after an intentional behavior change.
 */
public final class GoldenFile {
    private static final Path GOLDEN_DIR = Path.of("src/test/resources/golden");

    private GoldenFile() {
    }

    public static void assertMatches(String name, String actual) throws IOException {
        Path golden = GOLDEN_DIR.resolve(name);
        if (Boolean.getBoolean("golden.update") || !Files.exists(golden)) {
            Files.createDirectories(GOLDEN_DIR);
            Files.writeString(golden, actual, StandardCharsets.UTF_8);
            return;
        }
        String expected = Files.readString(golden, StandardCharsets.UTF_8);
        if (expected.equals(actual)) {
            return;
        }
        Path actualFile = Path.of(".gradle-build", "golden-actual", name);
        Files.createDirectories(actualFile.getParent());
        Files.writeString(actualFile, actual, StandardCharsets.UTF_8);
        List<String> expectedLines = expected.lines().toList();
        List<String> actualLines = actual.lines().toList();
        for (int i = 0; i < Math.max(expectedLines.size(), actualLines.size()); i++) {
            String e = i < expectedLines.size() ? expectedLines.get(i) : "<eof>";
            String a = i < actualLines.size() ? actualLines.get(i) : "<eof>";
            if (!e.equals(a)) {
                fail(name + " differs at line " + (i + 1) + "\n  expected: " + e + "\n  actual:   " + a
                        + "\n(full output written to " + actualFile + ")");
            }
        }
        fail(name + " differs (full output written to " + actualFile + ")");
    }
}
