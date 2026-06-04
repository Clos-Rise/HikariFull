package org.purpurmc.purpur;

import com.mojang.logging.LogUtils;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Async Chunk Sending — offloads chunk packet building to a worker thread pool.
 *
 * The expensive part of chunk sending is creating ClientboundLevelChunkWithLightPacket,
 * which serializes chunk data (blocks, biomes, light, heightmaps) into bytes.
 * This is CPU-intensive. By building packets on worker threads, the main thread
 * stays responsive during heavy chunk loading (teleports, elytra flight, world gen).
 *
 * The actual network send (connection.send()) and Bukkit event firing happen on
 * the main thread using pre-built packets from the completion queue.
 *
 * Integration with chunk priority system:
 * Chunks are sorted by priority (HIGHEST > HIGH > MEDIUM > LOW) BEFORE submission,
 * so closest chunks are processed first in each batch.
 */
public class HikariChunkSender {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ExecutorService executor;

    // Queue of pre-built packet batches ready to be sent on the main thread
    private static final ConcurrentLinkedQueue<PreBuiltChunkBatch> readyBatches = new ConcurrentLinkedQueue<>();

    /**
     * A batch of pre-built chunk packets ready for main-thread sending.
     */
    private record PreBuiltChunkBatch(
        ServerPlayer player,
        ServerLevel level,
        List<LevelChunk> chunks,
        List<ClientboundLevelChunkWithLightPacket> packets,
        List<ChunkPriority> priorities
    ) {}

    /**
     * Initialize the async chunk sender thread pool.
     */
    public static void init() {
        shutdown();
        int threads = HikariConfig.asyncChunkThreads;
        executor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "Hikari-ChunkBuilder");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
        HikariLogger.info("CHUNKS", "Initialized async chunk sender with " + threads + " threads");
    }

    /**
     * Submit chunks for async packet building.
     * Called from the main thread. Collects chunks and submits packet building to worker pool.
     *
     * Chunks are sorted by ChunkPriority (closest first) before submission.
     *
     * @param player       the player to send chunks to
     * @param chunksToSend the chunks to build
     * @param level        the server level
     */
    public static void submitChunkBuild(ServerPlayer player, List<LevelChunk> chunksToSend, ServerLevel level) {
        if (executor == null || executor.isShutdown() || chunksToSend.isEmpty()) {
            return;
        }

        // Sort chunks by priority (closest to player first)
        int playerChunkX = player.chunkPosition().x;
        int playerChunkZ = player.chunkPosition().z;

        // Build priority list and sort
        List<ChunkWithPriority> prioritized = new ArrayList<>(chunksToSend.size());
        for (LevelChunk chunk : chunksToSend) {
            ChunkPriority priority = ChunkPriority.fromChunkCoords(
                chunk.getPos().x, chunk.getPos().z, playerChunkX, playerChunkZ
            );
            prioritized.add(new ChunkWithPriority(chunk, priority));
        }
        prioritized.sort(Comparator.comparingInt(cp -> -cp.priority().ordinal())); // HIGHEST first

        // Extract sorted chunks
        List<LevelChunk> sortedChunks = new ArrayList<>(prioritized.size());
        List<ChunkPriority> priorities = new ArrayList<>(prioritized.size());
        for (ChunkWithPriority cp : prioritized) {
            sortedChunks.add(cp.chunk());
            priorities.add(cp.priority());
        }

        // Copy the chunk list for thread safety
        final List<LevelChunk> chunkCopy = new ArrayList<>(sortedChunks);
        final List<ChunkPriority> priorityCopy = new ArrayList<>(priorities);

        CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.nanoTime();

                // Build packets off the main thread — this is the expensive part
                List<ClientboundLevelChunkWithLightPacket> packets = new ArrayList<>(chunkCopy.size());
                boolean shouldModify = level.chunkPacketBlockController.shouldModify(player, chunkCopy.get(0));

                for (LevelChunk chunk : chunkCopy) {
                    packets.add(new ClientboundLevelChunkWithLightPacket(
                        chunk, level.getLightEngine(), null, null, shouldModify
                    ));
                }

                long duration = System.nanoTime() - startTime;
                HikariLogger.debugTimed("CHUNKS", "Built " + chunkCopy.size() + " chunk packets", duration);

                // Queue for main thread sending
                readyBatches.add(new PreBuiltChunkBatch(player, level, chunkCopy, packets, priorityCopy));
            } catch (Exception e) {
                HikariLogger.error("CHUNKS", "Error building chunk packets async", e);
            }
        }, executor);
    }

    /**
     * Process completed chunk batches on the main thread.
     * Sends pre-built packets and fires Bukkit events.
     *
     * @param maxBatches maximum number of batches to process this tick
     */
    public static void processCompletedBatches(int maxBatches) {
        int processed = 0;
        PreBuiltChunkBatch batch;
        while (processed < maxBatches && (batch = readyBatches.poll()) != null) {
            try {
                ServerGamePacketListenerImpl connection = batch.player.connection;

                // Send batch start marker
                connection.send(ClientboundChunkBatchStartPacket.INSTANCE);

                // Send pre-built packets and handle events on main thread
                for (int i = 0; i < batch.chunks.size(); i++) {
                    LevelChunk chunk = batch.chunks.get(i);
                    ClientboundLevelChunkWithLightPacket packet = batch.packets.get(i);
                    ChunkPriority priority = batch.priorities.get(i);

                    // Send the pre-built packet
                    connection.send(packet);

                    // Fire PlayerChunkLoadEvent on main thread (Bukkit requirement)
                    if (io.papermc.paper.event.packet.PlayerChunkLoadEvent.getHandlerList().getRegisteredListeners().length > 0) {
                        new io.papermc.paper.event.packet.PlayerChunkLoadEvent(
                            new org.bukkit.craftbukkit.CraftChunk(chunk),
                            batch.player.getBukkitEntity()
                        ).callEvent();
                    }

                    // Start debug tracking on main thread
                    batch.level.debugSynchronizers().startTrackingChunk(batch.player, chunk.getPos());

                    // Log priority for debug
                    HikariLogger.debug("CHUNKS", "Sent chunk [{},{}] priority={}",
                        chunk.getPos().x, chunk.getPos().z, priority.getDisplayName());
                }

                // Send batch finished marker
                connection.send(new ClientboundChunkBatchFinishedPacket(batch.chunks.size()));
                processed++;
            } catch (Exception e) {
                HikariLogger.error("CHUNKS", "Error sending async-built chunk batch", e);
            }
        }
    }

    /**
     * Get the number of completed batches waiting to be sent.
     */
    public static int getPendingBatchCount() {
        return readyBatches.size();
    }

    /**
     * Shutdown the async chunk sender.
     */
    public static void shutdown() {
        if (executor != null) {
            HikariLogger.info("CHUNKS", "Shutting down async chunk sender...");
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
        readyBatches.clear();
    }

    /**
     * Check if async chunk sending is active.
     */
    public static boolean isActive() {
        return executor != null && !executor.isShutdown() && HikariConfig.asyncChunkSending;
    }

    /**
     * Internal record for sorting chunks by priority.
     */
    private record ChunkWithPriority(LevelChunk chunk, ChunkPriority priority) {}
}
