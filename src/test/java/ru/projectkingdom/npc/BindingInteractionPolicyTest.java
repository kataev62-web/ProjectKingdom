package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BindingInteractionPolicyTest {
    @BeforeAll static void bootstrapMinecraftRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void onlyHeldBlockItemsBypassBindingAndCounterInteractions() {
        assertTrue(BindingInteractionPolicy.allowsPlacement(new ItemStack(Items.STONE)));
        assertFalse(BindingInteractionPolicy.allowsPlacement(new ItemStack(Items.STICK)));
        assertFalse(BindingInteractionPolicy.allowsPlacement(ItemStack.EMPTY));
        assertFalse(BindingInteractionPolicy.allowsPlacement(null));
    }
}
