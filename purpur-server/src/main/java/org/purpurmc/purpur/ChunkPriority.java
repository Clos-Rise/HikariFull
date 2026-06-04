package org.purpurmc.purpur;

/**
 * Chunk priority tiers based on distance from the nearest player.
 *
 * Priority affects:
 * - Chunk sending order (closest chunks sent first)
 * - Mob AI tick frequency
 * - Block entity tick scheduling
 * - Chunk loading urgency
 *
 * Distance is measured in chunks (1 chunk = 16 blocks).
 */
public enum ChunkPriority {
    /**
     * 0-3 chunks from player. Highest priority.
     * Mobs tick every tick, block entities tick normally, chunks sent immediately.
     */
    HIGHEST(0, 3, 1, "HIGHEST"),

    /**
     * 4-8 chunks from player. High priority.
     * Mobs tick every 2 ticks, block entities tick normally.
     */
    HIGH(4, 8, 2, "HIGH"),

    /**
     * 9-16 chunks from player. Medium priority.
     * Mobs tick every 4 ticks, block entities tick every 2 ticks.
     */
    MEDIUM(9, 16, 4, "MEDIUM"),

    /**
     * 17+ chunks from player. Low priority.
     * Mobs tick every 8 ticks, block entities tick every 4 ticks.
     */
    LOW(17, Integer.MAX_VALUE, 8, "LOW");

    private final int minChunkDistance;
    private final int maxChunkDistance;
    private final int mobTickDivisor;
    private final String displayName;

    ChunkPriority(int minChunkDistance, int maxChunkDistance, int mobTickDivisor, String displayName) {
        this.minChunkDistance = minChunkDistance;
        this.maxChunkDistance = maxChunkDistance;
        this.mobTickDivisor = mobTickDivisor;
        this.displayName = displayName;
    }

    /**
     * Determine chunk priority based on squared chunk distance from player.
     *
     * @param chunkDistSq squared distance in chunks
     * @return the priority level
     */
    public static ChunkPriority fromChunkDistance(int chunkDistSq) {
        // chunkDistSq is squared chunk distance
        // 0-3 chunks: 0-9 squared
        // 4-8 chunks: 16-64 squared
        // 9-16 chunks: 81-256 squared
        // 17+ chunks: 289+ squared
        if (chunkDistSq <= 9) return HIGHEST;    // 0-3 chunks
        if (chunkDistSq <= 64) return HIGH;       // 4-8 chunks
        if (chunkDistSq <= 256) return MEDIUM;    // 9-16 chunks
        return LOW;                                // 17+ chunks
    }

    /**
     * Determine chunk priority based on chunk coordinates relative to player.
     *
     * @param chunkX      chunk X coordinate
     * @param chunkZ      chunk Z coordinate
     * @param playerChunkX player chunk X coordinate
     * @param playerChunkZ player chunk Z coordinate
     * @return the priority level
     */
    public static ChunkPriority fromChunkCoords(int chunkX, int chunkZ, int playerChunkX, int playerChunkZ) {
        int dx = chunkX - playerChunkX;
        int dz = chunkZ - playerChunkZ;
        return fromChunkDistance(dx * dx + dz * dz);
    }

    /**
     * Get the minimum chunk distance for this priority level.
     */
    public int getMinChunkDistance() {
        return minChunkDistance;
    }

    /**
     * Get the maximum chunk distance for this priority level.
     */
    public int getMaxChunkDistance() {
        return maxChunkDistance;
    }

    /**
     * Get the mob tick divisor for this priority level.
     * Mobs only tick every N ticks where N is this divisor.
     */
    public int getMobTickDivisor() {
        return mobTickDivisor;
    }

    /**
     * Get the display name for logging.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if entities in chunks of this priority should tick AI this tick.
     *
     * @param tickCount current server tick count
     * @param entityId  entity ID (used for staggering)
     * @return true if the entity should run AI this tick
     */
    public boolean shouldTickEntity(int tickCount, int entityId) {
        if (this == HIGHEST) return true;
        return (tickCount + entityId) % mobTickDivisor == 0;
    }

    /**
     * Check if block entities in chunks of this priority should tick this tick.
     *
     * @param tickCount current server tick count
     * @param blockPos  block position hash (used for staggering)
     * @return true if the block entity should tick this tick
     */
    public boolean shouldTickBlockEntity(int tickCount, int blockPos) {
        int divisor = switch (this) {
            case HIGHEST -> 1;
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 4;
        };
        return (tickCount + blockPos) % divisor == 0;
    }
}
