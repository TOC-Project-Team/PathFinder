package org.momu.pathfinder.testsupport;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.BlockType;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.mock;

/**
 * Minimal registry so {@link Material#isSolid()}, {@link Material#isAir()} and {@link Material#getHardness()}
 * work in unit tests without a running server. Block properties come from {@link TestMaterials}.
 */
public final class TestRegistryAccess implements RegistryAccess {
    private final Map<NamespacedKey, BlockType> blockTypes = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private final Registry<BlockType> blockRegistry = Proxies.fake(Registry.class, (self, method, args) ->
            method.equals("get") && args.length == 1 && args[0] instanceof NamespacedKey key
                    ? blockTypes.computeIfAbsent(key, TestRegistryAccess::createBlockType)
                    : null);

    @Override
    @SuppressWarnings({"unchecked", "removal"})
    public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        if (type == BlockType.class) {
            return (Registry<T>) blockRegistry;
        }
        return mock(Registry.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key) {
        if (key == RegistryKey.BLOCK) {
            return (Registry<T>) blockRegistry;
        }
        return mock(Registry.class);
    }

    private static BlockType createBlockType(NamespacedKey key) {
        Material material = Material.valueOf(key.getKey().toUpperCase(Locale.ROOT));
        boolean air = TestMaterials.isAir(material);
        boolean solid = TestMaterials.isSolid(material);
        float hardness = TestMaterials.hardness(material);
        return Proxies.fake(BlockType.class, (self, method, args) -> switch (method) {
            case "isAir" -> air;
            case "isSolid", "isOccluding" -> solid;
            case "getHardness" -> hardness;
            case "getKey" -> key;
            default -> null;
        });
    }
}
