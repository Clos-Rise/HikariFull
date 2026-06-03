package net.minecraft.world.entity.npc;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.phys.AABB;

public class CatSpawner implements CustomSpawner {
    private static final int TICK_DELAY = 1200;
    private int nextTick;

    @Override
    public void tick(final ServerLevel level, final boolean spawnEnemies) {
        this.nextTick--;
        if (this.nextTick <= 0) {
            this.nextTick = level.purpurConfig.catSpawnDelay; // Purpur - Cat spawning options
            Player player = level.getRandomPlayer();
            if (player != null) {
                RandomSource random = level.getRandom();
                int x = (8 + random.nextInt(24)) * (random.nextBoolean() ? -1 : 1);
                int z = (8 + random.nextInt(24)) * (random.nextBoolean() ? -1 : 1);
                BlockPos spawnPos = player.blockPosition().offset(x, 0, z);
                int delta = 10;
                if (level.hasChunksAt(spawnPos.getX() - 10, spawnPos.getZ() - 10, spawnPos.getX() + 10, spawnPos.getZ() + 10)) {
                    if (SpawnPlacements.isSpawnPositionOk(EntityType.CAT, level, spawnPos)) {
                        if (level.isCloseToVillage(spawnPos, 2)) {
                            this.spawnInVillage(level, spawnPos);
                        } else if (level.structureManager().getStructureWithPieceAt(spawnPos, StructureTags.CATS_SPAWN_IN).isValid()) {
                            this.spawnInHut(level, spawnPos);
                        }
                    }
                }
            }
        }
    }

    private void spawnInVillage(final ServerLevel serverLevel, final BlockPos spawnPos) {
        // Purpur start - Cat spawning options
        int radius = serverLevel.purpurConfig.catSpawnVillageScanRange;
        if (radius <= 0) return;
        if (serverLevel.getPoiManager().getCountInRange(holder -> holder.is(PoiTypes.HOME), spawnPos, radius, PoiManager.Occupancy.IS_OCCUPIED) > 4L) {
            List<Cat> cats = serverLevel.getEntitiesOfClass(Cat.class, new AABB(spawnPos).inflate(radius, 8.0, radius));
        // Purpur end - Cat spawning options
            if (cats.size() < 5) {
                this.spawnCat(spawnPos, serverLevel, false);
            }
        }
    }

    private void spawnInHut(final ServerLevel level, final BlockPos spawnPos) {
        // Purpur start - Cat spawning options
        int radius = level.purpurConfig.catSpawnSwampHutScanRange;
        if (radius <= 0) return;
        List<Cat> cats = level.getEntitiesOfClass(Cat.class, new AABB(spawnPos).inflate(radius, 8.0, radius));
        // Purpur end - Cat spawning options
        if (cats.isEmpty()) {
            this.spawnCat(spawnPos, level, true);
        }
    }

    private void spawnCat(final BlockPos spawnPos, final ServerLevel level, final boolean makePersistent) {
        Cat cat = EntityType.CAT.create(level, EntitySpawnReason.NATURAL);
        if (cat != null) {
            cat.snapTo(spawnPos, 0.0F, 0.0F); // Paper - move up - Fix MC-147659
            cat.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), EntitySpawnReason.NATURAL, null);
            if (makePersistent) {
                cat.setPersistenceRequired();
            }

            level.addFreshEntityWithPassengers(cat);
        }
    }
}
