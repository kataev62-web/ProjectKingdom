package ru.projectkingdom.npc;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Read-only adapter for Minecraft's loaded Container inventory.
 * No chunk loads, writes, or automatic reconciliation are performed here.
 * All callers must run on the Minecraft server thread.
 */
public final class TradeMinecraftContainerView {
    private TradeMinecraftContainerView() {}

    public static TradeAtomicEconomy.Account snapshot(UUID accountId, long copper,
            Container container) throws IOException {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(container);
        if (copper < 0) throw new IOException("negative enterprise balance");
        List<TradeAtomicEconomy.Stack> slots = new ArrayList<>();
        int size = container.getContainerSize();
        if (size < 0 || size > 4096) throw new IOException("invalid container size");
        for (int i = 0; i < size; i++) {
            ItemStack item = container.getItem(i);
            if (item == null) throw new IOException("unreadable slot " + i);
            if (item.isEmpty()) { slots.add(null); continue; }
            var key = BuiltInRegistries.ITEM.getKey(item.getItem());
            if (key == null) throw new IOException("unregistered item in slot " + i);
            // Canonical tag-only payload; the item registry id and count are stored separately.
            CompoundTag tag = item.getTag();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                NbtIo.write(tag == null ? new CompoundTag() : tag, out);
            }
            try {
                slots.add(new TradeAtomicEconomy.Stack(key.toString(), bytes.toByteArray(),
                        item.getCount(), item.getMaxStackSize()));
            } catch (IllegalArgumentException invalid) {
                throw new IOException("unsupported stack in slot " + i, invalid);
            }
        }
        return new TradeAtomicEconomy.Account(accountId, copper, slots);
    }
}
