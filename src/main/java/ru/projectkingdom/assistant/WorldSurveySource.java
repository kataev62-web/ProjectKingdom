package ru.projectkingdom.assistant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import ru.projectkingdom.map.KingdomAtlasBuildingOutlines;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomEnterpriseData;

import java.util.ArrayList;
import java.util.List;

/** Read-only boundary used by the pure survey scanner. */
public interface WorldSurveySource {
    int minBuildHeight();
    int maxBuildHeight();
    boolean isChunkLoaded(int chunkX, int chunkZ);
    WorldSurveySnapshot.TerrainColumn sampleColumn(int x, int z);
    List<WorldSurveySnapshot.BuildingOutline> buildingOutlines(WorldSurveyService.SurveyRequest request);
    List<WorldSurveySnapshot.Plot> plots(WorldSurveyService.SurveyRequest request);
    List<WorldSurveySnapshot.EnterpriseCore> enterpriseCores(WorldSurveyService.SurveyRequest request);

    /** Forge adapter. Every block read starts from getChunkNow and therefore never loads a chunk. */
    static WorldSurveySource forPlayer(ServerPlayer player) {
        if (player == null || player.getServer() == null) throw new IllegalArgumentException("missing player");
        var level = player.serverLevel();
        return new WorldSurveySource() {
            @Override public int minBuildHeight() { return level.getMinBuildHeight(); }
            @Override public int maxBuildHeight() { return level.getMaxBuildHeight(); }

            @Override public boolean isChunkLoaded(int chunkX, int chunkZ) {
                return level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
            }

            @Override public WorldSurveySnapshot.TerrainColumn sampleColumn(int x, int z) {
                var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) throw new IllegalStateException("unloaded chunk");
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
                if (y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight())
                    throw new IllegalArgumentException("heightmap outside world");
                BlockPos position = new BlockPos(x, y, z);
                var state = chunk.getBlockState(position);
                if (state.isAir() && y > chunk.getMinBuildHeight()) {
                    position = position.below();
                    state = chunk.getBlockState(position);
                    y--;
                }
                boolean water = state.getFluidState().is(FluidTags.WATER);
                String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                boolean road = state.is(Blocks.DIRT_PATH) || blockId.contains("road") || blockId.contains("path");
                return new WorldSurveySnapshot.TerrainColumn(x, z, y, water, road);
            }

            @Override public List<WorldSurveySnapshot.BuildingOutline> buildingOutlines(
                    WorldSurveyService.SurveyRequest request) {
                List<WorldSurveySnapshot.BuildingOutline> result = new ArrayList<>();
                int minX = request.center().getX() - request.radius();
                int minZ = request.center().getZ() - request.radius();
                int maxX = request.center().getX() + request.radius() - 1;
                int maxZ = request.center().getZ() + request.radius() - 1;
                for (var outline : KingdomAtlasBuildingOutlines.forPlayer(player,
                        request.center().getX() >> 4, request.center().getZ() >> 4, 1)) {
                    if (outline.maxX() < minX || outline.maxZ() < minZ
                            || outline.minX() > maxX || outline.minZ() > maxZ) continue;
                    String id = outline.kind().name() + ":" + outline.minX() + ":" + outline.minZ();
                    result.add(new WorldSurveySnapshot.BuildingOutline(id, outline.kind().name(),
                            outline.minX(), outline.minZ(), outline.maxX(), outline.maxZ()));
                }
                return List.copyOf(result);
            }

            @Override public List<WorldSurveySnapshot.Plot> plots(WorldSurveyService.SurveyRequest request) {
                List<WorldSurveySnapshot.Plot> result = new ArrayList<>();
                int minChunkX = (request.center().getX() - request.radius()) >> 4;
                int minChunkZ = (request.center().getZ() - request.radius()) >> 4;
                int maxChunkX = (request.center().getX() + request.radius() - 1) >> 4;
                int maxChunkZ = (request.center().getZ() + request.radius() - 1) >> 4;
                for (var plot : KingdomCivicsData.get(player.getServer()).mapPlots(request.dimension())) {
                    if (plot.x() < minChunkX || plot.x() > maxChunkX
                            || plot.z() < minChunkZ || plot.z() > maxChunkZ) continue;
                    result.add(new WorldSurveySnapshot.Plot(plot.town().toString(),
                            plot.x(), plot.z(), plot.zone().name()));
                }
                return List.copyOf(result);
            }

            @Override public List<WorldSurveySnapshot.EnterpriseCore> enterpriseCores(
                    WorldSurveyService.SurveyRequest request) {
                List<WorldSurveySnapshot.EnterpriseCore> result = new ArrayList<>();
                var enterprises = KingdomEnterpriseData.get(player.getServer());
                long radiusSquared = (long) request.radius() * request.radius();
                for (var enterprise : enterprises.all()) {
                    var binding = enterprises.core(enterprise.id());
                    if (binding == null || !binding.active()
                            || !request.dimension().equals(binding.dimension())) continue;
                    BlockPos position = BlockPos.of(binding.packedPos());
                    long dx = (long) position.getX() - request.center().getX();
                    long dz = (long) position.getZ() - request.center().getZ();
                    if (dx * dx + dz * dz > radiusSquared) continue;
                    result.add(new WorldSurveySnapshot.EnterpriseCore(
                            enterprise.id().toString(), binding.kind().id(), position));
                }
                return List.copyOf(result);
            }
        };
    }
}
