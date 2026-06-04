package org.purpurmc.purpur;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Multithreaded Dimension Ticking (MDT) — ticks different dimensions in parallel.
 *
 * Each dimension is mostly independent: entities, chunks, block ticks are per-world.
 * Shared state (event flags, hopper optimization) is set on the main thread BEFORE
 * submitting parallel ticks. The main thread waits for all dimensions to complete
 * before proceeding to connection/player ticking.
 */
public class HikariDimensionTickPool {
    private static ForkJoinPool pool;
    private static int currentParallelism;

    /**
     * Initialize the thread pool. Called during server startup.
     */
    public static void init() {
        shutdown(); // Clean up any existing pool
        currentParallelism = calculateParallelism();
        pool = new ForkJoinPool(currentParallelism);
        HikariLogger.info("MDT", "Initialized dimension tick pool with " + currentParallelism + " threads");
    }

    /**
     * Calculate the optimal parallelism level.
     * Uses configured value, or auto-detects based on available processors.
     */
    private static int calculateParallelism() {
        if (HikariConfig.mdtThreads > 0) {
            return HikariConfig.mdtThreads;
        }
        // Auto: use min(CPU cores, 8) — no need for more than ~8 dimensions
        return Math.min(Runtime.getRuntime().availableProcessors(), 8);
    }

    /**
     * Tick all dimensions in parallel.
     *
     * @param levels    the list of all server levels to tick
     * @param haveTime  supplier indicating if the server has tick time budget remaining
     * @param profiler  the active profiler
     */
    public static void tickDimensions(List<ServerLevel> levels, BooleanSupplier haveTime, ProfilerFiller profiler) {
        if (pool == null || levels.size() <= 1) {
            // Fallback to sequential if pool not initialized or only 1 dimension
            tickSequential(levels, haveTime, profiler);
            return;
        }

        HikariLogger.debug("MDT", "Ticking {} dimensions in parallel", levels.size());

        List<ForkJoinTask<?>> tasks = new ArrayList<>(levels.size());

        for (ServerLevel level : levels) {
            ForkJoinTask<?> task = pool.submit(() -> {
                TickThread.setHikariTickThread(true); // Hikari - Mark MDT thread as tick thread
                try {
                    level.tick(haveTime);
                } catch (Throwable t) {
                    // Wrap exception with world context for better crash reports
                    CrashReport report = CrashReport.forThrowable(t, "Exception ticking world (Hikari MDT)");
                    level.fillReportDetails(report);
                    throw new ReportedException(report);
                } finally {
                    TickThread.setHikariTickThread(false); // Hikari - Clear flag
                }
            });
            tasks.add(task);
        }

        // Wait for all dimensions to complete
        for (ForkJoinTask<?> task : tasks) {
            try {
                task.join();
            } catch (ReportedException e) {
                // Re-throw crash reports immediately
                throw e;
            } catch (Throwable t) {
                CrashReport report = CrashReport.forThrowable(t, "Exception during parallel dimension tick (Hikari MDT)");
                throw new ReportedException(report);
            }
        }

        HikariLogger.debug("MDT", "All dimensions ticked successfully");
    }

    /**
     * Fallback sequential ticking — identical to vanilla behavior.
     */
    private static void tickSequential(List<ServerLevel> levels, BooleanSupplier haveTime, ProfilerFiller profiler) {
        for (ServerLevel level : levels) {
            try {
                level.tick(haveTime);
            } catch (Throwable t) {
                CrashReport report = CrashReport.forThrowable(t, "Exception ticking world");
                level.fillReportDetails(report);
                throw new ReportedException(report);
            }
        }
    }

    /**
     * Shutdown the thread pool. Called during server stop.
     */
    public static void shutdown() {
        if (pool != null) {
            HikariLogger.info("MDT", "Shutting down dimension tick pool...");
            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
            pool = null;
        }
    }

    /**
     * Get the current thread pool parallelism level.
     */
    public static int getParallelism() {
        return currentParallelism;
    }

    /**
     * Check if MDT is active and ready.
     */
    public static boolean isActive() {
        return pool != null && !pool.isShutdown() && HikariConfig.mdtEnabled;
    }
}
