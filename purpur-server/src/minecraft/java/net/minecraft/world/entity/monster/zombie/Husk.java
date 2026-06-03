package net.minecraft.world.entity.monster.zombie;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.camel.CamelHusk;
import net.minecraft.world.entity.monster.skeleton.Parched;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.LevelEvent;
import org.jspecify.annotations.Nullable;

public class Husk extends Zombie {
    private static final EntityDimensions BABY_DIMENSIONS = EntityDimensions.scalable(0.49F, 0.99F)
        .withEyeHeight(0.825F)
        .withAttachments(EntityAttachments.builder().attach(EntityAttachment.VEHICLE, 0.0F, 0.1875F, 0.0F));

    public Husk(final EntityType<? extends Husk> type, final Level level) {
        super(type, level);
        this.setShouldBurnInDay(false); // Purpur - API for any mob to burn daylight
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.huskRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.huskRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.huskControllable;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.huskMaxHealth);
    }

    @Override
    protected void randomizeReinforcementsChance() {
        this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(this.random.nextDouble() * this.level().purpurConfig.huskSpawnReinforcements);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Configurable jockey options
    @Override
    public boolean jockeyOnlyBaby() {
        return level().purpurConfig.huskJockeyOnlyBaby;
    }

    @Override
    public double jockeyChance() {
        return level().purpurConfig.huskJockeyChance;
    }

    @Override
    public boolean jockeyTryExistingChickens() {
        return level().purpurConfig.huskJockeyTryExistingChickens;
    }
    // Purpur end - Configurable jockey options

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.huskTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.huskAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    // Purpur start - Check mobGriefing Overrides
    @Override
    protected Boolean checkEntityPickUpLootOverride() {
        return this.level().purpurConfig.huskCanPickUpLoot;
    }
    // Purpur end - Check mobGriefing Overrides

    @Override
    public boolean isSunSensitive() {
        return this.shouldBurnInDay; // Purpur - moved to LivingEntity; keep methods for ABI compatibility - API for any mob to burn daylight
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.HUSK_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(final DamageSource source) {
        return SoundEvents.HUSK_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.HUSK_DEATH;
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.HUSK_STEP;
    }

    @Override
    public boolean doHurtTarget(final ServerLevel level, final Entity target) {
        boolean result = super.doHurtTarget(level, target);
        if (result && this.getMainHandItem().isEmpty() && target instanceof LivingEntity) {
            float difficulty = level.getCurrentDifficultyAt(this.blockPosition()).getEffectiveDifficulty();
            ((LivingEntity)target).addEffect(new MobEffectInstance(MobEffects.HUNGER, 140 * (int)difficulty), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
        }

        return result;
    }

    @Override
    protected boolean convertsInWater() {
        return true;
    }

    @Override
    protected void doUnderWaterConversion(final ServerLevel level) {
        this.convertToZombieType(level, EntityType.ZOMBIE);
        if (!this.isSilent()) {
            level.levelEvent(null, LevelEvent.SOUND_HUSK_TO_ZOMBIE, this.blockPosition(), 0);
        }
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level, final DifficultyInstance difficulty, final EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData
    ) {
        RandomSource random = level.getRandom();
        groupData = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        float difficultyModifier = difficulty.getSpecialMultiplier();
        if (spawnReason != EntitySpawnReason.CONVERSION) {
            this.setCanPickUpLoot(level.getLevel().paperConfig().entities.behavior.mobsCanAlwaysPickUpLoot.zombies || random.nextFloat() < 0.55F * difficultyModifier); // Paper - Add world settings for mobs picking up loot
        }

        if (groupData != null) {
            groupData = new Husk.HuskGroupData((Zombie.ZombieGroupData)groupData);
            ((Husk.HuskGroupData)groupData).triedToSpawnCamelHusk = spawnReason != EntitySpawnReason.NATURAL;
        }

        if (groupData instanceof Husk.HuskGroupData huskGroupData && !huskGroupData.triedToSpawnCamelHusk) {
            BlockPos pos = this.blockPosition();
            if (level.noCollision(EntityType.CAMEL_HUSK.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
                huskGroupData.triedToSpawnCamelHusk = true;
                if (random.nextFloat() < 0.1F) {
                    this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
                    CamelHusk camelHusk = EntityType.CAMEL_HUSK.create(this.level(), EntitySpawnReason.NATURAL);
                    if (camelHusk != null) {
                        camelHusk.setPos(this.getX(), this.getY(), this.getZ());
                        camelHusk.finalizeSpawn(level, difficulty, spawnReason, null);
                        this.startRiding(camelHusk, true, true);
                        level.addFreshEntity(camelHusk);
                        Parched parched = EntityType.PARCHED.create(this.level(), EntitySpawnReason.NATURAL);
                        if (parched != null) {
                            parched.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                            parched.finalizeSpawn(level, difficulty, spawnReason, null);
                            parched.startRiding(camelHusk, false, false);
                            level.addFreshEntityWithPassengers(parched);
                        }
                    }
                }
            }
        }

        return groupData;
    }

    @Override
    public EntityDimensions getDefaultDimensions(final Pose pose) {
        return this.isBaby() ? BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    public static class HuskGroupData extends Zombie.ZombieGroupData {
        public boolean triedToSpawnCamelHusk = false;

        public HuskGroupData(final Zombie.ZombieGroupData groupData) {
            super(groupData.isBaby, groupData.canSpawnJockey);
        }
    }
}
