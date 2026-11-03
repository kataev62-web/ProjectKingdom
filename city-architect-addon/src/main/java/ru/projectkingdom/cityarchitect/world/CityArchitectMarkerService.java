package ru.projectkingdom.cityarchitect.world;

import net.minecraft.core.BlockPos;
import ru.projectkingdom.cityarchitect.data.CityArchitectPlanData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class CityArchitectMarkerService {
    public static final int MAX_CHANGES_PER_TICK = 16;

    private final CityArchitectPlanData data;
    private final WorldAccess world;
    private final Map<UUID, ArrayDeque<CityArchitectPlanData.MarkerCell>> queues = new LinkedHashMap<>();

    public CityArchitectMarkerService(CityArchitectPlanData data, WorldAccess world) {
        this.data = Objects.requireNonNull(data, "data");
        this.world = Objects.requireNonNull(world, "world");
    }

    public synchronized void queue(UUID planId, List<CityArchitectPlanData.MarkerCell> input) {
        Objects.requireNonNull(planId, "planId");
        List<CityArchitectPlanData.MarkerCell> cells = List.copyOf(Objects.requireNonNull(input, "cells"));
        if (cells.size() > CityArchitectPlanData.MAX_MARKERS) {
            throw new IllegalArgumentException("Marker queue exceeds the plan limit");
        }
        var plan = data.plan(planId).orElseThrow(() -> new IllegalArgumentException("Unknown plan"));
        Set<BlockPos> alreadyApplied = new HashSet<>();
        plan.markerChanges().stream()
                .filter(change -> !change.restored())
                .map(CityArchitectPlanData.MarkerChange::position)
                .forEach(alreadyApplied::add);

        ArrayDeque<CityArchitectPlanData.MarkerCell> queue = queues.computeIfAbsent(
                planId, ignored -> new ArrayDeque<>());
        Set<BlockPos> scheduled = new HashSet<>(alreadyApplied);
        queue.stream().map(CityArchitectMarkerService::target).forEach(scheduled::add);
        for (CityArchitectPlanData.MarkerCell cell : cells) {
            BlockPos target = target(cell);
            if (scheduled.add(target)) {
                queue.addLast(cell);
            }
        }
        if ((long) plan.markerChanges().size() + queue.size() > CityArchitectPlanData.MAX_MARKERS) {
            queues.remove(planId);
            throw new IllegalArgumentException("Marker queue and journal exceed the plan limit");
        }
    }

    public synchronized TickResult tick(int requestedBudget) {
        int budget = Math.min(MAX_CHANGES_PER_TICK, Math.max(0, requestedBudget));
        int changed = 0;
        int refused = 0;
        int paused = 0;
        int attempts = 0;

        Iterator<Map.Entry<UUID, ArrayDeque<CityArchitectPlanData.MarkerCell>>> jobs =
                queues.entrySet().iterator();
        while (jobs.hasNext() && attempts < budget) {
            Map.Entry<UUID, ArrayDeque<CityArchitectPlanData.MarkerCell>> job = jobs.next();
            ArrayDeque<CityArchitectPlanData.MarkerCell> queue = job.getValue();
            while (!queue.isEmpty() && attempts < budget) {
                CityArchitectPlanData.MarkerCell marker = queue.peekFirst();
                BlockPos position = target(marker);
                if (!world.loaded(position)) {
                    paused++;
                    break;
                }
                attempts++;
                WorldCell before = world.inspect(position);
                if (!world.safeReplaceable(position, before)) {
                    queue.removeFirst();
                    refused++;
                    continue;
                }

                String markerState = markerState(marker.kind());
                CityArchitectPlanData.MarkerChange journal = new CityArchitectPlanData.MarkerChange(
                        position, marker.kind(), before.stateId(), markerState, false, false);
                int journalIndex = data.plan(job.getKey()).orElseThrow().markerChanges().size();
                data.recordMarkerChange(job.getKey(), journal);
                if (world.setExact(position, before.stateId(), markerState)) {
                    changed++;
                } else {
                    data.updateMarkerChange(job.getKey(), journalIndex, journal.restored(true));
                    refused++;
                }
                queue.removeFirst();
            }
            if (queue.isEmpty()) {
                jobs.remove();
            }
        }
        return new TickResult(changed, refused, paused);
    }

    public synchronized RestoreResult restore(UUID planId) {
        var plan = data.plan(planId).orElseThrow(() -> new IllegalArgumentException("Unknown plan"));
        int restored = 0;
        int conflicts = 0;
        int paused = 0;
        List<CityArchitectPlanData.MarkerChange> changes = new ArrayList<>(plan.markerChanges());
        for (int index = 0; index < changes.size(); index++) {
            CityArchitectPlanData.MarkerChange change = changes.get(index);
            if (change.restored()) {
                continue;
            }
            if (!world.loaded(change.position())) {
                paused++;
                continue;
            }
            boolean conflict = !world.inspect(change.position()).stateId().equals(change.markerState());
            if (!conflict) {
                conflict = !world.setExact(change.position(), change.markerState(), change.beforeState());
            }
            data.updateMarkerChange(planId, index, change.restored(conflict));
            if (conflict) {
                conflicts++;
            } else {
                restored++;
            }
        }
        if (paused == 0) {
            queues.remove(planId);
        }
        return new RestoreResult(restored, conflicts, paused);
    }

    public synchronized void resume(UUID planId) {
        ArrayDeque<CityArchitectPlanData.MarkerCell> queue = queues.get(planId);
        if (queue == null) {
            return;
        }
        Set<BlockPos> applied = new HashSet<>();
        data.plan(planId).orElseThrow(() -> new IllegalArgumentException("Unknown plan"))
                .markerChanges().stream()
                .filter(change -> !change.restored())
                .map(CityArchitectPlanData.MarkerChange::position)
                .forEach(applied::add);
        queue.removeIf(cell -> applied.contains(target(cell)));
        if (queue.isEmpty()) {
            queues.remove(planId);
        }
    }

    public synchronized boolean hasWork(UUID planId) {
        ArrayDeque<CityArchitectPlanData.MarkerCell> queue = queues.get(planId);
        return queue != null && !queue.isEmpty();
    }

    public static String markerState(CityArchitectPlanData.MarkerKind kind) {
        return switch (Objects.requireNonNull(kind, "kind")) {
            case ROAD -> "minecraft:yellow_wool";
            case ADMINISTRATION -> "minecraft:red_wool";
            case PRODUCTION -> "minecraft:orange_wool";
            case COMMERCE_STORAGE -> "minecraft:green_wool";
            case HOUSING -> "minecraft:light_blue_wool";
            case WATER_PORT -> "minecraft:blue_wool";
        };
    }

    private static BlockPos target(CityArchitectPlanData.MarkerCell cell) {
        return cell.position().above().immutable();
    }

    public interface WorldAccess {
        boolean loaded(BlockPos position);

        WorldCell inspect(BlockPos position);

        boolean safeReplaceable(BlockPos position, WorldCell cell);

        boolean setExact(BlockPos position, String expectedState, String replacementState);
    }

    public record WorldCell(String stateId, boolean hasBlockEntity, boolean unbreakable) {
        public WorldCell {
            stateId = Objects.requireNonNull(stateId, "stateId").trim();
            if (stateId.isEmpty()) {
                throw new IllegalArgumentException("stateId must not be blank");
            }
        }
    }

    public record TickResult(int changed, int refused, int paused) {
    }

    public record RestoreResult(int restored, int conflicts, int paused) {
    }
}
