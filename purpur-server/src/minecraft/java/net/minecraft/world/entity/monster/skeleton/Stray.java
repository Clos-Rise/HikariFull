package net.minecraft.world.entity.monster.skeleton;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

public class Stray extends AbstractSkeleton {
    public Stray(final EntityType<? extends Stray> type, final Level level) {
        super(type, level);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.strayRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.strayRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.strayControllable;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.strayMaxHealth);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.strayTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.strayAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    // Purpur start - Check mobGriefing Overrides
    @Override
    protected Boolean checkEntityPickUpLootOverride() {
        return this.level().purpurConfig.strayCanPickUpLoot;
    }
    // Purpur end - Check mobGriefing Overrides

    public static boolean checkStraySpawnRules(
        final EntityType<Stray> type, final ServerLevelAccessor level, final EntitySpawnReason spawnReason, final BlockPos pos, final RandomSource random
    ) {
        BlockPos checkSkyPos = pos;

        do {
            checkSkyPos = checkSkyPos.above();
        } while (level.getBlockState(checkSkyPos).is(Blocks.POWDER_SNOW));

        return Monster.checkMonsterSpawnRules(type, level, spawnReason, pos, random)
            && (EntitySpawnReason.isSpawner(spawnReason) || level.canSeeSky(checkSkyPos.below()));
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.STRAY_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(final DamageSource source) {
        return SoundEvents.STRAY_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.STRAY_DEATH;
    }

    @Override
    SoundEvent getStepSound() {
        return SoundEvents.STRAY_STEP;
    }

    @Override
    protected AbstractArrow getArrow(final ItemStack projectile, final float power, final @Nullable ItemStack firingWeapon) {
        AbstractArrow arrow = super.getArrow(projectile, power, firingWeapon);
        if (arrow instanceof Arrow) {
            ((Arrow)arrow).addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 600));
        }

        return arrow;
    }
}
