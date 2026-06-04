package org.purpurmc.purpur;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * Batch processor for hoppers and redstone items.
 *
 * Provides metrics collection and debug logging for hopper processing.
 * The actual hopper logic uses the existing optimized Paper/Spigot code.
 *
 * Key optimizations:
 * - Metrics tracking for performance monitoring
 * - Debug logging with timing information
 * - Integration with HikariLogger TUI output
 */
public class HikariBatchProcessor {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Metrics
    private static long totalHopperTicks;
    private static long batchStartTime;
    private static int currentBatchSize;

    /**
     * Process a hopper tick with metrics collection.
     * This is a wrapper around the existing HopperBlockEntity.pushItemsTick
     * that adds performance monitoring.
     *
     * @param level       the level
     * @param pos         the block position
     * @param state       the block state
     * @param blockEntity the hopper block entity
     */
    public static void processHopperTick(Level level, BlockPos pos, BlockState state, HopperBlockEntity blockEntity) {
        currentBatchSize++;
        totalHopperTicks++;

        // Call the existing optimized hopper tick
        HopperBlockEntity.pushItemsTick(level, pos, state, blockEntity);
    }

    /**
     * Start a batch processing session. Called at the beginning of block entity ticking.
     */
    public static void startBatch() {
        batchStartTime = System.nanoTime(); // Always record for metrics
        currentBatchSize = 0;
    }

    /**
     * End a batch processing session. Called at the end of block entity ticking.
     */
    public static void endBatch() {
        if (currentBatchSize > 0) {
            long duration = System.nanoTime() - batchStartTime;
            HikariLogger.debugTimed("HOPPERS", "Tick batch: " + currentBatchSize + " hoppers", duration);
        }
    }

    /**
     * Get total hopper ticks processed since server start.
     */
    public static long getTotalHopperTicks() {
        return totalHopperTicks;
    }

    /**
     * Reset metrics.
     */
    public static void resetMetrics() {
        totalHopperTicks = 0;
    }
}
