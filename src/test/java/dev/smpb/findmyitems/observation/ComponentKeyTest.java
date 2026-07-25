package dev.smpb.findmyitems.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The components key is the mod's notion of "the same item". Enchantments live in a data-driven
 * registry, so encoding them needs registry-aware ops — and the failure that used to be swallowed
 * here handed every enchanted stack the key of a plain one, which made Take pull all three diamond
 * swords out of a chest at once.
 */
final class ComponentKeyTest {
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = VanillaRegistries.createLookup();
    }

    @Test
    void enchantedVariantsGetDistinctKeys() {
        var plain = SlotReader.serializeComponents(DataComponentPatch.EMPTY, registries);
        var sharpness = key(Enchantments.SHARPNESS, 5);
        var smite = key(Enchantments.SMITE, 4);

        assertEquals("{}", plain);
        assertNotEquals(plain, sharpness);
        assertNotEquals(plain, smite);
        assertNotEquals(sharpness, smite);
    }

    @Test
    void enchantmentLevelIsPartOfTheKey() {
        assertNotEquals(key(Enchantments.SHARPNESS, 4), key(Enchantments.SHARPNESS, 5));
    }

    @Test
    void sameEnchantmentEncodesTheSameWayTwice() {
        assertEquals(key(Enchantments.SMITE, 4), key(Enchantments.SMITE, 4));
    }

    /**
     * Without registries the codec cannot encode — but the answer may never be {@code "{}"}, which
     * is a plain stack's key. Merging onto it is what let one Take empty out every variant.
     */
    @Test
    void unencodableKeyNeverCollidesWithAPlainStack() {
        var degraded = SlotReader.serializeComponents(patch(Enchantments.SHARPNESS, 5), null);

        assertNotEquals("{}", degraded);
        assertNotEquals(degraded, SlotReader.serializeComponents(patch(Enchantments.SMITE, 4), null));
    }

    private static String key(ResourceKey<Enchantment> enchantment, int level) {
        return SlotReader.serializeComponents(patch(enchantment, level), registries);
    }

    private static DataComponentPatch patch(ResourceKey<Enchantment> enchantment, int level) {
        var mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), level);
        return DataComponentPatch.builder().set(DataComponents.ENCHANTMENTS, mutable.toImmutable()).build();
    }
}
