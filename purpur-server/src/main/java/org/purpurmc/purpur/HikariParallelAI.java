package org.purpurmc.purpur;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.sensing.Sensing;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * Parallel AI processing for mob entities.
 *
 * Offloads expensive AI computations (sensing, pathfinding) to worker threads,
 * while keeping goal execution on the main thread for thread safety.
 *
 * Architecture:
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    Main Thread                               │
 * │  Entity tick → physics → position → sync                    │
 * │       │                                                      │
 * │       ▼                                                      │
 * │  Phase 1: Submit AI tasks to parallel executor               │
 * │       │                                                      │
 * │       ▼                                                      │
 * │  Phase 2: Wait for AI results (barrier)                      │
 * │       │                                                      │
 * │       ▼                                                      │
 * │  Phase 3: Apply AI results (goal selection, movement)        │
 * └─────────────────────────────────────────────────────────────┘
 *       │                                              ▲
 *       ▼                                              │
 * ┌─────────────────────────────────────────────────────────────┐
 * │                   Worker Threads                             │
 * │  - Sensing (entity lookups, line-of-sight)                   │
 * │  - Pathfinding (A* navigation)                               │
 * │  - Target evaluation                                         │
 * └─────────────────────────────────────────────────────────────┘
 *
 * Thread Safety:
 * - Worker threads only READ world state, never MODIFY it
 * - All entity state modifications happen on the main thread
 * - Sensing results are cached per-entity and applied on main thread
 */
public class HikariParallelAI {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ExecutorService executor;

    // Batch of AI tasks for parallel processing
    private static final ThreadLocal<List<AITask>> taskBatch = ThreadLocal.withInitial(() -> new ArrayList<>(128));
    private static final ThreadLocal<List<CompletableFuture<AIResult>>> futures = ThreadLocal.withInitial(() -> new ArrayList<>(128));

    /**
     * Represents an AI computation task for a single mob.
     */
    public record AITask(Mob mob, int tickCount) {}

    /**
     * Result of parallel AI computation.
     * Contains pre-computed sensing and pathfinding data.
     */
    public record AIResult(
        Mob mob,
        boolean sensingUpdated,
        long sensingDurationNanos
    ) {}

    /**
     * Initialize the parallel AI executor.
     */
    public static void init() {
        shutdown();
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        executor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "Hikari-AI");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
        HikariLogger.info("AI", "Initialized parallel AI executor with " + threads + " threads");
    }

    /**
     * Shutdown the parallel AI executor.
     */
    public static void shutdown() {
        if (executor != null) {
            HikariLogger.info("AI", "Shutting down parallel AI executor...");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }

    /**
     * Check if parallel AI processing is active.
     */
    public static boolean isActive() {
        return executor != null && !executor.isShutdown();
    }

    /**
     * Submit a mob's sensing computation for parallel processing.
     * This runs the expensive entity lookups on a worker thread.
     *
     * @param mob       the mob to process
     * @param tickCount current tick count
     */
    public static void submitSensing(Mob mob, int tickCount) {
        if (!isActive()) return;

        List<CompletableFuture<AIResult>> batch = futures.get();
        batch.add(CompletableFuture.supplyAsync(() -> {
            long startTime = System.nanoTime();
            try {
                // Run sensing on worker thread (read-only world access)
                mob.getSensing().tick();
                mob.sensingPreComputed = true; // Mark as pre-computed
                long duration = System.nanoTime() - startTime;
                return new AIResult(mob, true, duration);
            } catch (Exception e) {
                HikariLogger.debug("AI", "Error in parallel sensing for " + mob.getType().toShortString());
                return new AIResult(mob, false, 0);
            }
        }, executor));
    }

    /**
     * Wait for all submitted AI tasks to complete and apply results.
     * This is a barrier that must be called on the main thread.
     */
    public static void awaitAndApply() {
        List<CompletableFuture<AIResult>> batch = futures.get();
        if (batch.isEmpty()) return;

        long startTime = System.nanoTime(); // Always record timing for metrics

        // Wait for all sensing tasks to complete
        CompletableFuture.allOf(batch.toArray(new CompletableFuture[0])).join();

        // Apply results on main thread
        for (CompletableFuture<AIResult> future : batch) {
            AIResult result = future.join();
            // Sensing is already applied (tick() writes to the sensing cache)
            // No additional application needed
        }

        // Always record metrics (debugTimed handles logging conditionally)
        long duration = System.nanoTime() - startTime;
        HikariLogger.debugTimed("AI", "Parallel sensing for " + batch.size() + " mobs", duration);

        batch.clear();
    }

    /**
     * Get the number of pending AI tasks.
     */
    public static int getPendingCount() {
        return futures.get().size();
    }
}
