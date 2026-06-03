package net.minecraft.world.level.levelgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;

public class PhantomSpawner implements CustomSpawner {
    private int nextTick;

    @Override
    public void tick(final ServerLevel level, final boolean spawnEnemies) {
        if (spawnEnemies) {
            if (level.getGameRules().get(GameRules.SPAWN_PHANTOMS)) {
                // Paper start - Ability to control player's insomnia and phantoms
                if (level.paperConfig().entities.behavior.phantomsSpawnAttemptMaxSeconds <= 0 || level.paperConfig().entities.behavior.playerInsomniaStartTicks < 0) {
                    return;
                }
                // Paper end - Ability to control player's insomnia and phantoms
                RandomSource random = level.getRandom();
                this.nextTick--;
                if (this.nextTick <= 0) {
                    // Paper start - Ability to control player's insomnia and phantoms
                    int spawnAttemptMinSeconds = level.paperConfig().entities.behavior.phantomsSpawnAttemptMinSeconds;
                    int spawnAttemptMaxSeconds = level.paperConfig().entities.behavior.phantomsSpawnAttemptMaxSeconds;
                    this.nextTick += (spawnAttemptMinSeconds + random.nextInt(spawnAttemptMaxSeconds - spawnAttemptMinSeconds + 1)) * 20;
                    // Paper end - Ability to control player's insomnia and phantoms
                    if (level.getSkyDarken() >= level.purpurConfig.phantomSpawnMinSkyDarkness || !level.dimensionType().hasSkyLight()) { // Purpur - Add phantom spawning options
                        for (ServerPlayer player : level.players()) {
                            if (!player.isSpectator() && (!level.paperConfig().entities.behavior.phantomsDoNotSpawnOnCreativePlayers || !player.isCreative())) { // Paper - Add phantom creative and insomniac controls
                                BlockPos playerPos = player.blockPosition();
                                if (!level.dimensionType().hasSkyLight() || (!level.purpurConfig.phantomSpawnOnlyAboveSeaLevel || playerPos.getY() >= level.getSeaLevel()) && (!level.purpurConfig.phantomSpawnOnlyWithVisibleSky || level.canSeeSky(playerPos))) { // Purpur - Add phantom spawning options
                                    DifficultyInstance difficulty = level.getCurrentDifficultyAt(playerPos);
                                    if (difficulty.isHarderThan(random.nextFloat() * (float) level.purpurConfig.phantomSpawnLocalDifficultyChance)) { // Purpur - Add phantom spawning options
                                        ServerStatsCounter stats = player.getStats();
                                        int value = Mth.clamp(stats.getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)), 1, Integer.MAX_VALUE);
                                        int dayLength = 24000;
                                        if (random.nextInt(value) >= level.paperConfig().entities.behavior.playerInsomniaStartTicks) { // Paper - Ability to control player's insomnia and phantoms
                                            BlockPos spawnPos = playerPos.above(20 + random.nextInt(15))
                                                .east(-10 + random.nextInt(21))
                                                .south(-10 + random.nextInt(21));
                                            BlockState blockState = level.getBlockState(spawnPos);
                                            FluidState fluidState = level.getFluidState(spawnPos);
                                            if (NaturalSpawner.isValidEmptySpawnBlock(level, spawnPos, blockState, fluidState, EntityType.PHANTOM)) {
                                                SpawnGroupData groupData = null;
                                                int groupSize = level.purpurConfig.phantomSpawnMinPerAttempt + random.nextInt((level.purpurConfig.phantomSpawnMaxPerAttempt < 0 ? difficulty.getDifficulty().getId() : level.purpurConfig.phantomSpawnMaxPerAttempt - level.purpurConfig.phantomSpawnMinPerAttempt) + 1); // Purpur - Add phantom spawning options

                                                for (int i = 0; i < groupSize; i++) {
                                                    // Paper start - PhantomPreSpawnEvent
                                                    com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent event = new com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent(org.bukkit.craftbukkit.util.CraftLocation.toBukkit(spawnPos, level), player.getBukkitEntity(), org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL);
                                                    if (!event.callEvent()) {
                                                        if (event.shouldAbortSpawn()) {
                                                            break;
                                                        }
                                                        continue;
                                                    }
                                                    // Paper end - PhantomPreSpawnEvent
                                                    Phantom phantom = EntityType.PHANTOM.create(level, EntitySpawnReason.NATURAL);
                                                    if (phantom != null) {
                                                        phantom.spawningEntity = player.getUUID(); // Paper - PhantomPreSpawnEvent
                                                        phantom.snapTo(spawnPos, 0.0F, 0.0F);
                                                        groupData = phantom.finalizeSpawn(level, difficulty, EntitySpawnReason.NATURAL, groupData);
                                                        level.addFreshEntityWithPassengers(phantom, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL); // CraftBukkit
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
