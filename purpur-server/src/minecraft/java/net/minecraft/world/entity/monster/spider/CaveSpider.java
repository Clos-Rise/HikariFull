package net.minecraft.world.entity.monster.spider;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class CaveSpider extends Spider {
    public CaveSpider(final EntityType<? extends CaveSpider> type, final Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createCaveSpider() {
        return Spider.createAttributes().add(Attributes.MAX_HEALTH, 12.0);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.caveSpiderRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.caveSpiderRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.caveSpiderControllable;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.caveSpiderMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.caveSpiderScale);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.caveSpiderTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.caveSpiderAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    public boolean doHurtTarget(final ServerLevel level, final Entity target) {
        if (super.doHurtTarget(level, target)) {
            if (target instanceof LivingEntity) {
                int poisonTime = 0;
                if (this.level().getDifficulty() == Difficulty.NORMAL) {
                    poisonTime = 7;
                } else if (this.level().getDifficulty() == Difficulty.HARD) {
                    poisonTime = 15;
                }

                if (poisonTime > 0) {
                    ((LivingEntity)target).addEffect(new MobEffectInstance(MobEffects.POISON, poisonTime * 20, 0), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level, final DifficultyInstance difficulty, final EntitySpawnReason spawnReason, final @Nullable SpawnGroupData groupData
    ) {
        return groupData;
    }

    @Override
    public Vec3 getVehicleAttachmentPoint(final Entity vehicle) {
        return vehicle.getBbWidth() <= this.getBbWidth() ? new Vec3(0.0, 0.21875 * this.getScale(), 0.0) : super.getVehicleAttachmentPoint(vehicle);
    }
}
