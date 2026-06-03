package net.minecraft.world.entity.animal.equine;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class Mule extends AbstractChestedHorse {
    public Mule(final EntityType<? extends Mule> type, final Level level) {
        super(type, level);
    }

    // Purpur start - Ridables
    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.muleRidableInWater;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public float generateMaxHealth(net.minecraft.util.RandomSource random) {
        return (float) generateMaxHealth(this.level().purpurConfig.muleMaxHealthMin, this.level().purpurConfig.muleMaxHealthMax);
    }

    @Override
    public double generateJumpStrength(net.minecraft.util.RandomSource random) {
        return generateJumpStrength(this.level().purpurConfig.muleJumpStrengthMin, this.level().purpurConfig.muleJumpStrengthMax);
    }

    @Override
    public double generateSpeed(net.minecraft.util.RandomSource random) {
        return generateSpeed(this.level().purpurConfig.muleMovementSpeedMin, this.level().purpurConfig.muleMovementSpeedMax);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Make entity breeding times configurable
    @Override
    public int getPurpurBreedTime() {
        return this.level().purpurConfig.muleBreedingTicks;
    }
    // Purpur end - Make entity breeding times configurable

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.muleTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.muleAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.MULE_AMBIENT;
    }

    @Override
    protected SoundEvent getAngrySound() {
        return SoundEvents.MULE_ANGRY;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.MULE_DEATH;
    }

    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.MULE_EAT;
    }

    @Override
    public SoundEvent getHurtSound(final DamageSource source) {
        return SoundEvents.MULE_HURT;
    }

    @Override
    protected void playJumpSound() {
        this.playSound(SoundEvents.MULE_JUMP, 0.4F, 1.0F);
    }

    @Override
    protected void playChestEquipsSound() {
        this.playSound(SoundEvents.MULE_CHEST, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(final ServerLevel level, final AgeableMob partner) {
        return EntityType.MULE.create(level, EntitySpawnReason.BREEDING);
    }
}
