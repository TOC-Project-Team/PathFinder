package org.momu.pathfinder.api;

/**
 * Static access point for the {@link PathFinderAPI}.
 * <pre>{@code
 * PathFinderAPI api = PathFinderProvider.get();
 * api.navigateToLocation(player, location, "Spawn");
 * }</pre>
 */
public final class PathFinderProvider {
    private static volatile PathFinderAPI instance;

    private PathFinderProvider() {
    }

    /**
     * @return the API instance
     * @throws IllegalStateException if PathFinder is not enabled
     */
    public static PathFinderAPI get() {
        PathFinderAPI api = instance;
        if (api == null) {
            throw new IllegalStateException("PathFinder is not enabled. Add 'depend: [PathFinder]' "
                    + "or 'softdepend: [PathFinder]' to your plugin.yml.");
        }
        return api;
    }

    /**
     * @return whether the API is currently available
     */
    public static boolean isAvailable() {
        return instance != null;
    }

    /**
     * Internal: called by PathFinder on enable/disable.
     */
    public static void register(PathFinderAPI api) {
        instance = api;
    }

    /**
     * Internal: called by PathFinder on disable.
     */
    public static void unregister() {
        instance = null;
    }
}
