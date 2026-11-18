package ru.projectkingdom.npc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Guards production integration wiring without requiring a launched Forge world. */
final class KingdomPhysicalCashAliasWiringTest {
    private static String read(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+name));
    }

    @Test void bothPreflightAndMoveMustRejectUnknownOrAliasedPhysicalSlotsFirst() throws Exception {
        String cash=read("npc/KingdomPhysicalCash.java");
        int p=cash.indexOf("public static boolean canMove(");
        int m=cash.indexOf("public static boolean move(");
        assertTrue(p>=0&&m>p);
        assertTrue(cash.substring(p,m).contains("if(!independentPhysicalSlots(source,target))return false"));
        String move=cash.substring(m,cash.indexOf("    /** Trusted NPC settlement:",m));
        assertTrue(move.startsWith("public static boolean move("));
        assertTrue(move.contains("if(!independentPhysicalSlots(source,target))return false"));
        assertTrue(move.indexOf("independentPhysicalSlots")<move.indexOf("snapshot(source)"));
        assertTrue(move.contains("PhysicalCashCommitGate.commit("));
        assertTrue(move.contains("Arrays.equals(coinCounts(oldSource,oldTarget)"));
        assertTrue(move.contains("matches(source,taken)")&&move.contains("matches(target,added)"));
    }

    @Test void pairAndVaultViewsPropagateUnderlyingSlotIdentities() throws Exception {
        String resolver=read("npc/KingdomContainerAccess.java");
        String vault=read("administration/KingdomBankVaultRooms.java");
        assertTrue(resolver.contains("interface PhysicalSlotProvider"));
        assertTrue(resolver.contains("TrackedCompoundContainer extends CompoundContainer"));
        assertTrue(resolver.contains("return new TrackedCompoundContainer(container,other)"));
        assertFalse(resolver.contains("return new CompoundContainer(container,other)"));
        assertTrue(vault.contains("VaultContainer implements Container, KingdomContainerAccess.PhysicalSlotProvider"));
        assertTrue(vault.contains("KingdomContainerAccess.physicalSlot(source.chest(),source.index())"));
    }

    @Test void unknownModdedInventoryWrappersRemainFailClosed() throws Exception {
        String resolver=read("npc/KingdomContainerAccess.java");
        assertTrue(resolver.contains("if(view instanceof PhysicalSlotProvider provider)"));
        assertTrue(resolver.contains("type==SimpleContainer.class"));
        assertTrue(resolver.contains("return null;"));
        assertTrue(resolver.contains("type==SimpleContainer.class || type==ChestBlockEntity.class"));
        assertTrue(resolver.contains("return new KingdomPhysicalSlotGuard.Slot(view,slot);"));
        assertTrue(resolver.contains("return null;"),
                "Unknown wrapper classes must not silently receive guessed backing identities");
    }

    @Test void cannotMergeTaggedCoinsOrChangeDenominations() throws Exception {
        String cash=read("npc/KingdomPhysicalCash.java");
        assertTrue(cash.contains("present.hasTag()"),"Tags must not merge with plain coins");
        assertTrue(cash.contains("stack.hasTag()"),"Tagged coins are not eligible for automatic physical withdrawal");
        assertTrue(cash.contains("coinCounts("),"Copper/silver/gold must be conserved separately");
        assertTrue(cash.contains("planExactMoveDeposit("));
        assertFalse(cash.substring(cash.indexOf("public static boolean move("),
                cash.indexOf("    /** Trusted NPC settlement:")).contains("planDeposit(target,amount)"));
    }
}
