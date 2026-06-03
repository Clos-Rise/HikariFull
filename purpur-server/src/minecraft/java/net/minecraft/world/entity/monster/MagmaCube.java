package net.minecraft.world.entity.monster;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;

public class MagmaCube extends Slime {
    public MagmaCube(final EntityType<? extends MagmaCube> type, final Level level) {
        super(type, level);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.magmaCubeRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.magmaCubeRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.magmaCubeControllable;
    }

    @Override
    public float getJumpPower() {
        return 0.42F * this.getBlockJumpFactor(); // from EntityLiving
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    protected String getMaxHealthEquation() {
        return level().purpurConfig.magmaCubeMaxHealth;
    }

    @Override
    protected String getAttackDamageEquation() {
        return level().purpurConfig.magmaCubeAttackDamage;
    }

    @Override
    protected java.util.Map<Integer, Double> getMaxHealthCache() {
        return level().purpurConfig.magmaCubeMaxHealthCache;
    }

    @Override
    protected java.util.Map<Integer, Double> getAttackDamageCache() {
        return level().purpurConfig.magmaCubeAttackDamageCache;
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.magmaCubeTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.magmaCubeAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.2F);
    }

    public static boolean checkMagmaCubeSpawnRules(
        final EntityType<MagmaCube> type, final LevelAccessor level, final EntitySpawnReason spawnReason, final BlockPos pos, final RandomSource random
    ) {
        // Purpur start - Config to disable hostile mob spawn on ice
        if (net.minecraft.world.entity.monster.Monster.canSpawnInBlueAndPackedIce(level, pos)) {
            return false;
        }
        // Purpur end - Config to disable hostile mob spawn on ice
        return level.getDifficulty() != Difficulty.PEACEFUL;
    }

    @Override
    public void setSize(final int size, final boolean updateHealth) {
        super.setSize(size, updateHealth);
        this.getAttribute(Attributes.ARMOR).setBaseValue(size * 3);
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    @Override
    protected ParticleOptions getParticleType() {
        return ParticleTypes.FLAME;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    protected int getJumpDelay() {
        return super.getJumpDelay() * 4;
    }

    @Override
    protected void decreaseSquish() {
        this.targetSquish *= 0.9F;
    }

    @Override
    public void jumpFromGround() {
        Vec3 movement = this.getDeltaMovement();
        float sizeJumpBoostPower = this.getSize() * 0.1F;
        this.setDeltaMovement(movement.x, this.getJumpPower() + sizeJumpBoostPower, movement.z);
        this.needsSync = true;
        this.actualJump = false; // Purpur - Ridables
    }

    @Override
    protected void jumpInLiquid(final TagKey<Fluid> type) {
        if (type == FluidTags.LAVA) {
            Vec3 movement = this.getDeltaMovement();
            this.setDeltaMovement(movement.x, 0.22F + this.getSize() * 0.05F, movement.z);
            this.needsSync = true;
        } else {
            super.jumpInLiquid(type);
        }
    }

    @Override
    protected boolean isDealsDamage() {
        return this.isEffectiveAi();
    }

    @Override
    protected float getAttackDamage() {
        return super.getAttackDamage() + 2.0F;
    }

    @Override
    public SoundEvent getHurtSound(final DamageSource source) {
        return this.isTiny() ? SoundEvents.MAGMA_CUBE_HURT_SMALL : SoundEvents.MAGMA_CUBE_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return this.isTiny() ? SoundEvents.MAGMA_CUBE_DEATH_SMALL : SoundEvents.MAGMA_CUBE_DEATH;
    }

    @Override
    protected SoundEvent getSquishSound() {
        return this.isTiny() ? SoundEvents.MAGMA_CUBE_SQUISH_SMALL : SoundEvents.MAGMA_CUBE_SQUISH;
    }

    @Override
    protected SoundEvent getJumpSound() {
        return SoundEvents.MAGMA_CUBE_JUMP;
    }
}
