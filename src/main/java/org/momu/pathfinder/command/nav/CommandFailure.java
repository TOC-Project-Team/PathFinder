package org.momu.pathfinder.command.nav;

/**
 * Aborts a {@code /toc nav} subcommand; the message is shown to the sender in red.
 */
final class CommandFailure extends RuntimeException {
    CommandFailure(String message) {
        super(message, null, false, false);
    }
}
