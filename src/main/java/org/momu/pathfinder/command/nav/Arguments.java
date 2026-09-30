package org.momu.pathfinder.command.nav;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Parsing helpers for {@code /toc nav} arguments.
 */
final class Arguments {
    private Arguments() {
    }

    /**
     * Re-splits raw command arguments so that names can contain spaces: {@code "my home"} is one token, and
     * {@code \"} / {@code \\} escape a quote or backslash.
     */
    static List<String> tokenize(String[] args, int startIndex) {
        String joined = String.join(" ", List.of(args).subList(Math.min(startIndex, args.length), args.length));
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < joined.length(); i++) {
            char c = joined.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ' ' && !quoted) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else if (c == '\\' && i + 1 < joined.length()
                    && (joined.charAt(i + 1) == '"' || joined.charAt(i + 1) == '\\')) {
                current.append(joined.charAt(++i));
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /** @return the number, or {@code null} if the text is not one */
    static Double parseDouble(String text) {
        try {
            return Double.parseDouble(text);
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isNumber(String text) {
        return text != null && !text.isEmpty() && parseDouble(text) != null;
    }

    /** Formats a coordinate without trailing zeros, e.g. {@code 12} or {@code 3.5}. */
    static String formatCoordinate(double value) {
        return new DecimalFormat("0.########").format(value);
    }

    /** Reads a {@code --page=N} option; invalid or missing values give page 1. */
    static int parsePage(String token) {
        if (token == null || !token.startsWith("--page=")) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(token.substring("--page=".length())));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
