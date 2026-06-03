package net.minecraft.world.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

public class PoisonMobEffect extends MobEffect {
    public static final int DAMAGE_INTERVAL = 25;

    protected PoisonMobEffect(final MobEffectCategory category, final int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(final ServerLevel level, final LivingEntity mob, final int amplification) {
        if (mob.getHealth() > mob.level().purpurConfig.entityMinimalHealthPoison) { // Purpur
            mob.hurtServer(level, mob.damageSources().magic().knownCause(org.bukkit.event.entity.EntityDamageEvent.DamageCause.POISON), mob.level().purpurConfig.entityPoisonDegenerationAmount); // CraftBukkit // Purpur - Config MobEffect by world
        }

        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(final int tickCount, final int amplification) {
        int interval = 25 >> amplification;
        return interval <= 0 || tickCount % interval == 0;
    }
}
