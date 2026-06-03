package net.minecraft.world.entity.animal.equine;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class Donkey extends AbstractChestedHorse {
    public Donkey(final EntityType<? extends Donkey> type, final Level level) {
        super(type, level);
    }

    // Purpur start - Ridables
    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.donkeyRidableInWater;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public float generateMaxHealth(net.minecraft.util.RandomSource random) {
        return (float) generateMaxHealth(this.level().purpurConfig.donkeyMaxHealthMin, this.level().purpurConfig.donkeyMaxHealthMax);
    }

    @Override
    public double generateJumpStrength(net.minecraft.util.RandomSource random) {
        return generateJumpStrength(this.level().purpurConfig.donkeyJumpStrengthMin, this.level().purpurConfig.donkeyJumpStrengthMax);
    }

    @Override
    public double generateSpeed(net.minecraft.util.RandomSource random) {
        return generateSpeed(this.level().purpurConfig.donkeyMovementSpeedMin, this.level().purpurConfig.donkeyMovementSpeedMax);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Make entity breeding times configurable
    @Override
    public int getPurpurBreedTime() {
        return this.level().purpurConfig.donkeyBreedingTicks;
    }
    // Purpur end - Make entity breeding times configurable

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.donkeyTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.donkeyAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.DONKEY_AMBIENT;
    }

    @Override
    protected SoundEvent getAngrySound() {
        return SoundEvents.DONKEY_ANGRY;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.DONKEY_DEATH;
    }

    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.DONKEY_EAT;
    }

    @Override
    public SoundEvent getHurtSound(final DamageSource source) {
        return SoundEvents.DONKEY_HURT;
    }

    @Override
    public boolean canMate(final Animal partner) {
        return partner != this && (partner instanceof Donkey || partner instanceof Horse) && this.canParent() && ((AbstractHorse)partner).canParent();
    }

    @Override
    protected void playJumpSound() {
        this.playSound(SoundEvents.DONKEY_JUMP, 0.4F, 1.0F);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(final ServerLevel level, final AgeableMob partner) {
        EntityType<? extends AbstractHorse> babyType = partner instanceof Horse ? EntityType.MULE : EntityType.DONKEY;
        AbstractHorse baby = babyType.create(level, EntitySpawnReason.BREEDING);
        if (baby != null) {
            this.setOffspringAttributes(partner, baby);
        }

        return baby;
    }
}
