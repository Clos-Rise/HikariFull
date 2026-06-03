package net.minecraft.world.ticks;

import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

public class LevelChunkTicks<T> implements TickContainerAccess<T>, SerializableTickContainer<T>, ca.spottedleaf.moonrise.patches.chunk_system.ticks.ChunkSystemLevelChunkTicks { // Paper - rewrite chunk system
    private final Queue<ScheduledTick<T>> tickQueue = new PriorityQueue<>(ScheduledTick.DRAIN_ORDER);
    private @Nullable List<SavedTick<T>> pendingTicks;
    private final Set<ScheduledTick<?>> ticksPerPosition = new ObjectOpenCustomHashSet<>(ScheduledTick.UNIQUE_TICK_HASH);
    private @Nullable BiConsumer<LevelChunkTicks<T>, ScheduledTick<T>> onTickAdded;

    // Paper start - rewrite chunk system
    /*
     * Since ticks are saved using relative delays, we need to consider the entire tick list dirty when there are scheduled ticks
     * and the last saved tick is not equal to the current tick
     */
    /*
     * In general, it would be nice to be able to "re-pack" ticks once the chunk becomes non-ticking again, but that is a
     * bit out of scope for the chunk system
     */

    private boolean dirty;
    private long lastSaved = Long.MIN_VALUE;

    @Override
    public final boolean moonrise$isDirty(final long tick) {
        return this.dirty || (!this.tickQueue.isEmpty() && tick != this.lastSaved);
    }

    @Override
    public final void moonrise$clearDirty() {
        this.dirty = false;
    }
    // Paper end - rewrite chunk system

    public LevelChunkTicks() {
    }

    public LevelChunkTicks(final List<SavedTick<T>> pendingTicks) {
        this.pendingTicks = pendingTicks;

        for (SavedTick<T> pendingTick : pendingTicks) {
            this.ticksPerPosition.add(ScheduledTick.probe(pendingTick.type(), pendingTick.pos()));
        }
    }

    public void setOnTickAdded(final @Nullable BiConsumer<LevelChunkTicks<T>, ScheduledTick<T>> onTickAdded) {
        this.onTickAdded = onTickAdded;
    }

    public @Nullable ScheduledTick<T> peek() {
        return this.tickQueue.peek();
    }

    public @Nullable ScheduledTick<T> poll() {
        ScheduledTick<T> result = this.tickQueue.poll();
        if (result != null) {
            this.ticksPerPosition.remove(result); this.dirty = true; // Paper - rewrite chunk system
        }

        return result;
    }

    @Override
    public void schedule(final ScheduledTick<T> tick) {
        if (this.ticksPerPosition.add(tick)) {
            this.scheduleUnchecked(tick); this.dirty = true; // Paper - rewrite chunk system
        }
    }

    private void scheduleUnchecked(final ScheduledTick<T> tick) {
        this.tickQueue.add(tick);
        if (this.onTickAdded != null) {
            this.onTickAdded.accept(this, tick);
        }
    }

    @Override
    public boolean hasScheduledTick(final BlockPos pos, final T type) {
        return this.ticksPerPosition.contains(ScheduledTick.probe(type, pos));
    }

    public void removeIf(final Predicate<ScheduledTick<T>> test) {
        Iterator<ScheduledTick<T>> iterator = this.tickQueue.iterator();

        while (iterator.hasNext()) {
            ScheduledTick<T> tick = iterator.next();
            if (test.test(tick)) {
                iterator.remove(); this.dirty = true; // Paper - rewrite chunk system
                this.ticksPerPosition.remove(tick);
            }
        }
    }

    public Stream<ScheduledTick<T>> getAll() {
        return this.tickQueue.stream();
    }

    @Override
    public int count() {
        return this.tickQueue.size() + (this.pendingTicks != null ? this.pendingTicks.size() : 0);
    }

    @Override
    public List<SavedTick<T>> pack(final long currentTick) {
        this.lastSaved = currentTick; // Paper - rewrite chunk system
        List<SavedTick<T>> ticks = new ArrayList<>(this.tickQueue.size());
        if (this.pendingTicks != null) {
            ticks.addAll(this.pendingTicks);
        }

        for (ScheduledTick<T> tick : this.tickQueue) {
            ticks.add(tick.toSavedTick(currentTick));
        }

        return ticks;
    }

    public void unpack(final long currentTick) {
        if (this.pendingTicks != null) {
            this.lastSaved = currentTick; // Paper - rewrite chunk system
            int subTickBase = -this.pendingTicks.size();

            for (SavedTick<T> pendingTick : this.pendingTicks) {
                this.scheduleUnchecked(pendingTick.unpack(currentTick, subTickBase++));
            }
        }

        this.pendingTicks = null;
    }
}
