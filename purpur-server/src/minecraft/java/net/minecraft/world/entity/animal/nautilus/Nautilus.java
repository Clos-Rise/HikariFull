package net.minecraft.world.entity.animal.nautilus;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class Nautilus extends AbstractNautilus {
    private static final int NAUTILUS_TOTAL_AIR_SUPPLY = 300;
    private static final Brain.Provider<Nautilus> BRAIN_PROVIDER = Brain.provider(
        List.of(MemoryModuleType.ANGRY_AT, MemoryModuleType.ATTACK_TARGET_COOLDOWN),
        List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_ADULT, SensorType.NEAREST_PLAYERS, SensorType.HURT_BY, SensorType.NAUTILUS_TEMPTATIONS),
        var0 -> NautilusAi.getActivities()
    );

    public Nautilus(final EntityType<? extends Nautilus> type, final Level level) {
        super(type, level);
    }

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.nautilusMaxHealth);
        this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(this.level().purpurConfig.nautilusMovementSpeed);
        this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(this.level().purpurConfig.nautilusAttackDamage);
        this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(this.level().purpurConfig.nautilusKnockbackResistance);
        this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE).setBaseValue(this.level().purpurConfig.nautilusScale);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Make entity breeding times configurable
    @Override
    public int getPurpurBreedTime() {
        return this.level().purpurConfig.nautilusBreedingTicks;
    }
    // Purpur end - Make entity breeding times configurable

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.nautilusAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    protected Brain<Nautilus> makeBrain(final Brain.Packed packedBrain) {
        return BRAIN_PROVIDER.makeBrain(this, packedBrain);
    }

    @Override
    public Brain<Nautilus> getBrain() {
        return (Brain<Nautilus>) super.getBrain();
    }

    @Override
    public @Nullable Nautilus getBreedOffspring(final ServerLevel level, final AgeableMob partner) {
        Nautilus baby = EntityType.NAUTILUS.create(level, EntitySpawnReason.BREEDING);
        if (baby != null && this.isTame()) {
            baby.setOwnerReference(this.getOwnerReference());
            baby.setTame(true, true);
        }

        return baby;
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("nautilusBrain");
        this.getBrain().tick(level, this);
        profiler.pop();
        profiler.push("nautilusActivityUpdate");
        NautilusAi.updateActivity(this);
        profiler.pop();
        super.customServerAiStep(level);
    }

    @Override
    public SoundEvent getAmbientSound() {
        if (this.isBaby()) {
            return this.isUnderWater() ? SoundEvents.BABY_NAUTILUS_AMBIENT : SoundEvents.BABY_NAUTILUS_AMBIENT_ON_LAND;
        } else {
            return this.isUnderWater() ? SoundEvents.NAUTILUS_AMBIENT : SoundEvents.NAUTILUS_AMBIENT_ON_LAND;
        }
    }

    @Override
    public SoundEvent getHurtSound(final DamageSource source) {
        if (this.isBaby()) {
            return this.isUnderWater() ? SoundEvents.BABY_NAUTILUS_HURT : SoundEvents.BABY_NAUTILUS_HURT_ON_LAND;
        } else {
            return this.isUnderWater() ? SoundEvents.NAUTILUS_HURT : SoundEvents.NAUTILUS_HURT_ON_LAND;
        }
    }

    @Override
    public SoundEvent getDeathSound() {
        if (this.isBaby()) {
            return this.isUnderWater() ? SoundEvents.BABY_NAUTILUS_DEATH : SoundEvents.BABY_NAUTILUS_DEATH_ON_LAND;
        } else {
            return this.isUnderWater() ? SoundEvents.NAUTILUS_DEATH : SoundEvents.NAUTILUS_DEATH_ON_LAND;
        }
    }

    @Override
    protected SoundEvent getDashSound() {
        return this.isUnderWater() ? SoundEvents.NAUTILUS_DASH : SoundEvents.NAUTILUS_DASH_ON_LAND;
    }

    @Override
    protected SoundEvent getDashReadySound() {
        return this.isUnderWater() ? SoundEvents.NAUTILUS_DASH_READY : SoundEvents.NAUTILUS_DASH_READY_ON_LAND;
    }

    @Override
    protected void playEatingSound() {
        SoundEvent nautilusEatSound = this.isBaby() ? SoundEvents.BABY_NAUTILUS_EAT : SoundEvents.NAUTILUS_EAT;
        this.makeSound(nautilusEatSound);
    }

    @Override
    public SoundEvent getSwimSound() {
        return this.isBaby() ? SoundEvents.BABY_NAUTILUS_SWIM : SoundEvents.NAUTILUS_SWIM;
    }

    @Override
    public int getMaxAirSupply() {
        return this.maxAirTicks; // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    }
    // CraftBukkit start - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    @Override
    public int getDefaultMaxAirSupply() {
        return NAUTILUS_TOTAL_AIR_SUPPLY;
    }
    // CraftBukkit end

    protected void handleAirSupply(final ServerLevel level, final int preTickAirSupply) {
        if (this.isAlive() && !this.isInWater()) {
            this.setAirSupply(preTickAirSupply - 1);
            if (this.getAirSupply() <= -20) {
                this.setAirSupply(0);
                this.hurtServer(level, this.damageSources().dryOut(), 2.0F);
            }
        } else {
            this.setAirSupply(this.maxAirTicks); // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
        }
    }

    @Override
    public void baseTick() {
        int airSupply = this.getAirSupply();
        super.baseTick();
        if (!this.isNoAi() && this.level() instanceof ServerLevel serverLevel) {
            this.handleAirSupply(serverLevel, airSupply);
        }
    }

    @Override
    public boolean canBeLeashed() {
        return !this.isAggravated();
    }
}
