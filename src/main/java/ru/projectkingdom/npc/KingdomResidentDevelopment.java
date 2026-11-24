package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The development of the residents on their own.
 *
 * <p>The mechanics of the settlement are implemented on the contracts of the
 * Kingdom and never on the JAR of the reference mod: the skills, the career,
 * the personal goals, the purchases, the claims and the inheritance live here.
 *
 * <p>Two residents can never hold the same claim: a job, a lot or an object of
 * property is given to exactly one resident. The inheritance of one death is
 * transferred exactly once, also when the event of the death is processed again
 * after a restart.
 */
public final class KingdomResidentDevelopment extends SavedData {
    public static final String DATA_NAME = "kingdom_resident_development_v1";

    /** The skills of one resident. */
    public record Skill(String name, int level, int xp) {
        public Skill {
            Objects.requireNonNull(name, "name");
            if (level < 0 || xp < 0) throw new IllegalArgumentException("the skill is not usable");
        }
    }

    /** One step of the career of a resident. The history is never rewritten. */
    public record CareerStep(long day, String job, UUID enterprise) {
        public CareerStep {
            Objects.requireNonNull(job, "job");
        }
    }

    /** A personal goal of a resident. */
    public record Goal(UUID resident, String kind, String target, long deadlineDay) {
        public Goal {
            Objects.requireNonNull(resident, "resident");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(target, "target");
        }
    }

    /** The exclusive claim of one object: a job, a lot or a piece of property. */
    public record Claim(UUID owner, String kind, String target, long day) {
        public Claim {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(target, "target");
        }
    }

    /** The transfer of one asset of a dead resident to an heir. */
    public record Inheritance(UUID dead, UUID heir, String asset, long day) {
        public Inheritance {
            Objects.requireNonNull(dead, "dead");
            Objects.requireNonNull(heir, "heir");
            Objects.requireNonNull(asset, "asset");
        }
    }

    private final Map<UUID, Map<String, Skill>> skills = new LinkedHashMap<>();
    private final Map<UUID, List<CareerStep>> careers = new LinkedHashMap<>();
    private final Map<UUID, List<Goal>> goals = new LinkedHashMap<>();
    private final Map<String, Claim> claims = new LinkedHashMap<>();
    private final List<Inheritance> inheritances = new ArrayList<>();
    /** The purchases of the residents: who bought what and at which price. */
    private final List<String> purchases = new ArrayList<>();

    public static KingdomResidentDevelopment get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(KingdomResidentDevelopment::load, KingdomResidentDevelopment::new, DATA_NAME);
    }

    public static KingdomResidentDevelopment load(CompoundTag root) {
        KingdomResidentDevelopment data = new KingdomResidentDevelopment();
        ListTag skillList = root.getList("Skills", Tag.TAG_COMPOUND);
        for (int i = 0; i < skillList.size(); i++) {
            CompoundTag entry = skillList.getCompound(i);
            if (!entry.hasUUID("Resident")) continue;
            UUID resident = entry.getUUID("Resident");
            var map = data.skills.computeIfAbsent(resident, id -> new LinkedHashMap<>());
            ListTag names = entry.getList("Skills", Tag.TAG_COMPOUND);
            for (int k = 0; k < names.size(); k++) {
                CompoundTag skill = names.getCompound(k);
                map.put(skill.getString("Name"),
                        new Skill(skill.getString("Name"), skill.getInt("Level"), skill.getInt("Xp")));
            }
        }
        ListTag careerList = root.getList("Careers", Tag.TAG_COMPOUND);
        for (int i = 0; i < careerList.size(); i++) {
            CompoundTag entry = careerList.getCompound(i);
            if (!entry.hasUUID("Resident")) continue;
            UUID resident = entry.getUUID("Resident");
            List<CareerStep> steps = data.careers.computeIfAbsent(resident, id -> new ArrayList<>());
            ListTag items = entry.getList("Steps", Tag.TAG_COMPOUND);
            for (int k = 0; k < items.size(); k++) {
                CompoundTag step = items.getCompound(k);
                UUID enterprise = step.hasUUID("Enterprise") ? step.getUUID("Enterprise") : null;
                steps.add(new CareerStep(step.getLong("Day"), step.getString("Job"), enterprise));
            }
        }
        ListTag goalList = root.getList("Goals", Tag.TAG_COMPOUND);
        for (int i = 0; i < goalList.size(); i++) {
            CompoundTag entry = goalList.getCompound(i);
            if (!entry.hasUUID("Resident")) continue;
            data.goals.computeIfAbsent(entry.getUUID("Resident"), id -> new ArrayList<>())
                    .add(new Goal(entry.getUUID("Resident"), entry.getString("Kind"),
                            entry.getString("Target"), entry.getLong("Deadline")));
        }
        ListTag claimList = root.getList("Claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < claimList.size(); i++) {
            CompoundTag entry = claimList.getCompound(i);
            if (!entry.hasUUID("Owner")) continue;
            Claim claim = new Claim(entry.getUUID("Owner"), entry.getString("Kind"),
                    entry.getString("Target"), entry.getLong("Day"));
            data.claims.put(data.claimKey(claim.kind(), claim.target()), claim);
        }
        ListTag inheritanceList = root.getList("Inheritances", Tag.TAG_COMPOUND);
        for (int i = 0; i < inheritanceList.size(); i++) {
            CompoundTag entry = inheritanceList.getCompound(i);
            if (!entry.hasUUID("Dead") || !entry.hasUUID("Heir")) continue;
            data.inheritances.add(new Inheritance(entry.getUUID("Dead"), entry.getUUID("Heir"),
                    entry.getString("Asset"), entry.getLong("Day")));
        }
        ListTag purchaseList = root.getList("Purchases", Tag.TAG_COMPOUND);
        for (int i = 0; i < purchaseList.size(); i++) {
            CompoundTag entry = purchaseList.getCompound(i);
            data.purchases.add(entry.getString("Line"));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag root) {
        ListTag skillList = new ListTag();
        for (var entry : skills.entrySet()) {
            CompoundTag resident = new CompoundTag();
            resident.putUUID("Resident", entry.getKey());
            ListTag names = new ListTag();
            for (Skill skill : entry.getValue().values()) {
                CompoundTag tag = new CompoundTag();
                tag.putString("Name", skill.name());
                tag.putInt("Level", skill.level());
                tag.putInt("Xp", skill.xp());
                names.add(tag);
            }
            resident.put("Skills", names);
            skillList.add(resident);
        }
        root.put("Skills", skillList);
        ListTag careerList = new ListTag();
        for (var entry : careers.entrySet()) {
            CompoundTag resident = new CompoundTag();
            resident.putUUID("Resident", entry.getKey());
            ListTag steps = new ListTag();
            for (CareerStep step : entry.getValue()) {
                CompoundTag tag = new CompoundTag();
                tag.putLong("Day", step.day());
                tag.putString("Job", step.job());
                if (step.enterprise() != null) tag.putUUID("Enterprise", step.enterprise());
                steps.add(tag);
            }
            resident.put("Steps", steps);
            careerList.add(resident);
        }
        root.put("Careers", careerList);
        ListTag goalList = new ListTag();
        for (var entry : goals.entrySet()) {
            for (Goal goal : entry.getValue()) {
                CompoundTag tag = new CompoundTag();
                tag.putUUID("Resident", goal.resident());
                tag.putString("Kind", goal.kind());
                tag.putString("Target", goal.target());
                tag.putLong("Deadline", goal.deadlineDay());
                goalList.add(tag);
            }
        }
        root.put("Goals", goalList);
        ListTag claimList = new ListTag();
        for (Claim claim : claims.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Owner", claim.owner());
            tag.putString("Kind", claim.kind());
            tag.putString("Target", claim.target());
            tag.putLong("Day", claim.day());
            claimList.add(tag);
        }
        root.put("Claims", claimList);
        ListTag inheritanceList = new ListTag();
        for (Inheritance inheritance : inheritances) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Dead", inheritance.dead());
            tag.putUUID("Heir", inheritance.heir());
            tag.putString("Asset", inheritance.asset());
            tag.putLong("Day", inheritance.day());
            inheritanceList.add(tag);
        }
        root.put("Inheritances", inheritanceList);
        ListTag purchaseList = new ListTag();
        for (String line : purchases) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Line", line);
            purchaseList.add(tag);
        }
        root.put("Purchases", purchaseList);
        return root;
    }

    private String claimKey(String kind, String target) {
        return kind + "|" + target;
    }

    // --- skills ---------------------------------------------------------------

    /** The skills of a resident. */
    public Map<String, Skill> skillsOf(UUID resident) {
        return skills.getOrDefault(resident, Map.of());
    }

    /**
     * Adds experience to a skill of a resident.
     *
     * @return the new skill.
     */
    public Skill award(UUID resident, String skill, int xp) {
        Objects.requireNonNull(resident, "resident");
        if (skill == null || skill.isBlank() || xp <= 0) throw new IllegalArgumentException("the skill is not usable");
        var map = skills.computeIfAbsent(resident, id -> new LinkedHashMap<>());
        Skill current = map.getOrDefault(skill, new Skill(skill, 0, 0));
        int total = current.xp() + xp;
        int level = total / 100;
        Skill updated = new Skill(skill, level, total);
        map.put(skill, updated);
        setDirty();
        return updated;
    }

    // --- career ---------------------------------------------------------------

    /** The career of a resident. The history is never rewritten. */
    public List<CareerStep> careerOf(UUID resident) {
        return List.copyOf(careers.getOrDefault(resident, List.of()));
    }

    /** Records a step of the career. */
    public CareerStep recordCareer(UUID resident, long day, String job, UUID enterprise) {
        Objects.requireNonNull(resident, "resident");
        if (job == null || job.isBlank()) throw new IllegalArgumentException("the job is not usable");
        CareerStep step = new CareerStep(day, job, enterprise);
        careers.computeIfAbsent(resident, id -> new ArrayList<>()).add(step);
        setDirty();
        return step;
    }

    // --- goals ----------------------------------------------------------------

    /** The personal goals of a resident. */
    public List<Goal> goalsOf(UUID resident) {
        return List.copyOf(goals.getOrDefault(resident, List.of()));
    }

    /** Adds a personal goal. */
    public Goal addGoal(UUID resident, String kind, String target, long deadlineDay) {
        Goal goal = new Goal(resident, kind, target, deadlineDay);
        goals.computeIfAbsent(resident, id -> new ArrayList<>()).add(goal);
        setDirty();
        return goal;
    }

    /** Removes the goals of a resident that reached their deadline. */
    public int removeExpiredGoals(UUID resident, long day) {
        List<Goal> list = goals.get(resident);
        if (list == null) return 0;
        int removed = 0;
        for (int index = list.size() - 1; index >= 0; index--) {
            if (list.get(index).deadlineDay() > day) continue;
            list.remove(index);
            removed++;
        }
        if (removed > 0) setDirty();
        return removed;
    }

    // --- claims ---------------------------------------------------------------

    /**
     * Takes the exclusive claim of an object.
     *
     * @return true when the resident got it. A second resident never gets the
     *         same object.
     */
    public boolean claim(UUID owner, String kind, String target, long day) {
        Objects.requireNonNull(owner, "owner");
        if (kind == null || kind.isBlank() || target == null || target.isBlank())
            throw new IllegalArgumentException("the claim is not usable");
        String key = claimKey(kind, target);
        Claim existing = claims.get(key);
        if (existing != null) return existing.owner().equals(owner);
        claims.put(key, new Claim(owner, kind, target, day));
        setDirty();
        return true;
    }

    /** The claim of an object, null when it is free. */
    public Claim claimOf(String kind, String target) {
        return claims.get(claimKey(kind, target));
    }

    /** Releases the claim of a resident. */
    public boolean release(UUID owner, String kind, String target) {
        String key = claimKey(kind, target);
        Claim existing = claims.get(key);
        if (existing == null || !existing.owner().equals(owner)) return false;
        claims.remove(key);
        setDirty();
        return true;
    }

    /** The claims of a resident. */
    public List<Claim> claimsOf(UUID owner) {
        List<Claim> out = new ArrayList<>();
        for (Claim claim : claims.values()) if (claim.owner().equals(owner)) out.add(claim);
        return List.copyOf(out);
    }

    // --- inheritance ----------------------------------------------------------

    /**
     * Transfers one asset of a dead resident to an heir.
     *
     * <p>The transfer happens exactly once: a repeated event of the death, for
     * example after a restart, does not transfer the asset a second time.
     *
     * @return true when the transfer happened now.
     */
    public boolean inherit(UUID dead, UUID heir, String asset, long day) {
        Objects.requireNonNull(dead, "dead");
        Objects.requireNonNull(heir, "heir");
        if (asset == null || asset.isBlank()) throw new IllegalArgumentException("the asset is not usable");
        for (Inheritance existing : inheritances)
            if (existing.dead().equals(dead) && existing.asset().equals(asset)) return false;
        inheritances.add(new Inheritance(dead, heir, asset, day));
        setDirty();
        return true;
    }

    /** The transfers of a dead resident. */
    public List<Inheritance> inheritancesOf(UUID dead) {
        List<Inheritance> out = new ArrayList<>();
        for (Inheritance inheritance : inheritances) if (inheritance.dead().equals(dead)) out.add(inheritance);
        return List.copyOf(out);
    }

    /** The assets that were already transferred to an heir. */
    public Set<String> inheritedAssets(UUID dead, UUID heir) {
        Set<String> out = new LinkedHashSet<>();
        for (Inheritance inheritance : inheritances)
            if (inheritance.dead().equals(dead) && inheritance.heir().equals(heir))
                out.add(inheritance.asset());
        return out;
    }

    // --- purchases ------------------------------------------------------------

    /**
     * Records a purchase of a resident.
     *
     * <p>Only really reserved money and really available goods may be
     * recorded: the caller checks the wallet and the shop before.
     */
    public boolean recordPurchase(UUID resident, String item, int amount, long priceCopper) {
        Objects.requireNonNull(resident, "resident");
        if (item == null || item.isBlank() || amount <= 0 || priceCopper < 0)
            throw new IllegalArgumentException("the purchase is not usable");
        purchases.add(resident + "|" + item + "|" + amount + "|" + priceCopper);
        setDirty();
        return true;
    }

    /** The purchases of a resident. */
    public List<String> purchasesOf(UUID resident) {
        List<String> out = new ArrayList<>();
        for (String line : purchases) if (line.startsWith(resident + "|")) out.add(line);
        return List.copyOf(out);
    }
}
