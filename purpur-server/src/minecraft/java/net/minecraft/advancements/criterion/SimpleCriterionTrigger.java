package net.minecraft.advancements.criterion;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.Validatable;
import net.minecraft.world.level.storage.loot.ValidationContextSource;

public abstract class SimpleCriterionTrigger<T extends SimpleCriterionTrigger.SimpleInstance> implements CriterionTrigger<T> {
    // private final Map<PlayerAdvancements, Set<CriterionTrigger.Listener<T>>> players = Maps.newIdentityHashMap(); // Paper - fix PlayerAdvancements leak; moved into PlayerAdvancements to fix memory leak

    @Override
    public final void addPlayerListener(final PlayerAdvancements player, final CriterionTrigger.Listener<T> listener) {
        player.criterionData.computeIfAbsent(this, managerx -> Sets.newHashSet()).add(listener); // Paper - fix PlayerAdvancements leak
    }

    @Override
    public final void removePlayerListener(final PlayerAdvancements player, final CriterionTrigger.Listener<T> listener) {
        Set<CriterionTrigger.Listener<T>> listeners = (Set) player.criterionData.get(this); // Paper - fix PlayerAdvancements leak
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                player.criterionData.remove(this); // Paper - fix PlayerAdvancements leak
            }
        }
    }

    @Override
    public final void removePlayerListeners(final PlayerAdvancements player) {
        player.criterionData.remove(this); // Paper - fix PlayerAdvancements leak
    }

    protected void trigger(final ServerPlayer player, final Predicate<T> matcher) {
        PlayerAdvancements advancements = player.getAdvancements();
        Set<CriterionTrigger.Listener<T>> allListeners = (Set) advancements.criterionData.get(this); // Paper - fix PlayerAdvancements leak
        if (allListeners != null && !allListeners.isEmpty()) {
            LootContext playerContext = null; // EntityPredicate.createContext(player, player); // Paper - Perf: lazily create LootContext for criterions
            List<CriterionTrigger.Listener<T>> listeners = null;

            for (CriterionTrigger.Listener<T> listener : allListeners) {
                T triggerInstance = listener.trigger();
                if (matcher.test(triggerInstance)) {
                    Optional<ContextAwarePredicate> predicate = triggerInstance.player();
                    if (predicate.isEmpty() || predicate.get().matches(playerContext = (playerContext == null ? EntityPredicate.createContext(player, player) : playerContext))) { // Paper - Perf: lazily create LootContext for criterions
                        if (listeners == null) {
                            listeners = Lists.newArrayList();
                        }

                        listeners.add(listener);
                    }
                }
            }

            if (listeners != null) {
                for (CriterionTrigger.Listener<T> listener : listeners) {
                    listener.run(advancements);
                }
            }
        }
    }

    public interface SimpleInstance extends CriterionTriggerInstance {
        @Override
        default void validate(final ValidationContextSource validator) {
            Validatable.validate(validator.entityContext(), "player", this.player());
        }

        Optional<ContextAwarePredicate> player();
    }
}
