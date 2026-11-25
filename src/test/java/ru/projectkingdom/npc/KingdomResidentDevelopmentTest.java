package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

/**
 * The development of the residents on their own.
 *
 * <p>The rules that matter: the career keeps its history, a claim of an
 * object belongs to exactly one resident, and the inheritance of one death is
 * transferred exactly once, also when the event is processed again.
 */
final class KingdomResidentDevelopmentTest {

    @Test void theSkillGrowsWithTheConfirmedWork() {
        KingdomResidentDevelopment data = new KingdomResidentDevelopment();
        UUID resident = UUID.randomUUID();
        var first = data.award(resident, "mining", 40);
        assertEquals(0, first.level(), "forty points are not a level");
        assertEquals(40, first.xp());
        var second = data.award(resident, "mining", 60);
        assertEquals(1, second.level(), "a hundred points are a level");
        assertEquals(100, second.xp());
        assertEquals(1, data.skillsOf(resident).size(), "the skill of the resident is known");
    }

    @Test void theCareerKeepsEveryStep() {
        KingdomResidentDevelopment data = new KingdomResidentDevelopment();
        UUID resident = UUID.randomUUID();
        UUID farm = UUID.randomUUID();
        data.recordCareer(resident, 1, "farmer", farm);
        data.recordCareer(resident, 5, "miner", null);
        assertEquals(2, data.careerOf(resident).size(), "the change of the profession keeps the history");
        assertEquals("farmer", data.careerOf(resident).get(0).job());
        assertEquals("miner", data.careerOf(resident).get(1).job());
    }

    @Test void twoResidentsNeverHoldTheSameClaim() {
        KingdomResidentDevelopment data = new KingdomResidentDevelopment();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(data.claim(first, "lot", "parcel-7", 1), "the first resident gets the lot");
        assertFalse(data.claim(second, "lot", "parcel-7", 1), "the second resident does not get the same lot");
        assertTrue(data.claim(first, "lot", "parcel-7", 1), "the owner keeps the lot");
        assertEquals(first, data.claimOf("lot", "parcel-7").owner());
        assertEquals(1, data.claimsOf(first).size());
    }

    @Test void theClaimOfAnotherKindDoesNotBlock() {
        KingdomResidentDevelopment data = new KingdomResidentDevelopment();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(data.claim(first, "lot", "parcel-7", 1));
        assertTrue(data.claim(second, "job", "blacksmith", 1), "another kind is another object");
    }

    @Test void theInheritanceIsTransferredExactlyOnce() {
        KingdomResidentDevelopment data = new KingdomResidentDevelopment();
        UUID dead = UUID.randomUUID();
        UUID heir = UUID.randomUUID();
        assertTrue(data.inherit(dead, heir, "hammer", 10), "the first transfer happens");
        assertFalse(data.inherit(dead, heir, "hammer", 10), "the repeated event transfers nothing again");
        assertEquals(1, data.inheritancesOf(dead).size());
        assertTrue(data.inheritedAssets(dead, heir).contains("hammer"));
    }

    @Test void theInheritanceOfAnotherAssetIsPossible() {
        KingdomResidentDevelopment data = new KingdomResidentDevelopment();
        UUID dead = UUID.randomUUID();
        UUID heir = UUID.randomUUID();
        assertTrue(data.inherit(dead, heir, "hammer", 10));
        assertTrue(data.inherit(dead, heir, "plot", 10), "another asset is another transfer");
        assertEquals(2, data.inheritancesOf(dead).size());
    }

    @Test void theExpiredGoalsAreRemoved() {
        KingdomResidentDevelopment data = new KingdomResidentDevelopment();
        UUID resident = UUID.randomUUID();
        data.addGoal(resident, "build", "granary", 5);
        data.addGoal(resident, "buy", "wheat", 50);
        assertEquals(2, data.goalsOf(resident).size());
        assertEquals(1, data.removeExpiredGoals(resident, 10), "the goal of the day five is over");
        assertEquals(1, data.goalsOf(resident).size());
    }

    @Test void thePurchaseIsRecordedOnlyOnce() {
        KingdomResidentDevelopment data = new KingdomResidentDevelopment();
        UUID resident = UUID.randomUUID();
        assertTrue(data.recordPurchase(resident, "minecraft:wheat", 10, 40));
        assertEquals(1, data.purchasesOf(resident).size());
        assertEquals(0, data.purchasesOf(UUID.randomUUID()).size(), "the purchases belong to one resident");
    }

    @Test void theStateSurvivesASaveAndALoad() {
        KingdomResidentDevelopment data = new KingdomResidentDevelopment();
        UUID resident = UUID.randomUUID();
        UUID heir = UUID.randomUUID();
        data.award(resident, "mining", 150);
        data.recordCareer(resident, 3, "miner", UUID.randomUUID());
        data.addGoal(resident, "build", "granary", 9);
        data.claim(resident, "lot", "parcel-7", 4);
        UUID dead = UUID.randomUUID();
        data.inherit(dead, heir, "hammer", 6);
        data.recordPurchase(resident, "minecraft:wheat", 3, 12);

        CompoundTag root = data.save(new CompoundTag());
        KingdomResidentDevelopment loaded = KingdomResidentDevelopment.load(root);
        assertEquals(1, loaded.skillsOf(resident).size(), "the skills survive");
        assertEquals(1, loaded.skillsOf(resident).get("mining").level());
        assertEquals(1, loaded.careerOf(resident).size(), "the career survives");
        assertEquals(1, loaded.goalsOf(resident).size(), "the goals survive");
        assertNotNull(loaded.claimOf("lot", "parcel-7"), "the claim survives");
        assertEquals(resident, loaded.claimOf("lot", "parcel-7").owner());
        assertEquals(1, loaded.inheritancesOf(dead).size(), "the inheritance survives");
        assertEquals(1, loaded.purchasesOf(resident).size(), "the purchases survive");
    }

    @Test void theReleaseOfAClaimFreesTheObject() {
        KingdomResidentDevelopment data = new KingdomResidentDevelopment();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        data.claim(first, "lot", "parcel-7", 1);
        assertTrue(data.release(first, "lot", "parcel-7"), "the owner may release");
        assertNull(data.claimOf("lot", "parcel-7"));
        assertTrue(data.claim(second, "lot", "parcel-7", 2), "the object is free again");
        assertFalse(data.release(first, "lot", "parcel-7"), "a stranger may not release");
    }
}
