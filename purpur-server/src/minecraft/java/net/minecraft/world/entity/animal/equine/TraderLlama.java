package net.minecraft.world.entity.animal.equine;

import java.util.EnumSet;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class TraderLlama extends Llama {
    private static final int DEFAULT_DESPAWN_DELAY = 47999;
    private int despawnDelay = 47999;

    public TraderLlama(final EntityType<? extends TraderLlama> type, final Level level) {
        super(type, level);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.traderLlamaRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.traderLlamaRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.traderLlamaControllable;
    }

    @Override
    public boolean isSaddled() {
        return super.isSaddled() || isTamed();
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public float generateMaxHealth(net.minecraft.util.RandomSource random) {
        return (float) generateMaxHealth(this.level().purpurConfig.traderLlamaMaxHealthMin, this.level().purpurConfig.traderLlamaMaxHealthMax);
    }

    @Override
    public double generateJumpStrength(net.minecraft.util.RandomSource random) {
        return generateJumpStrength(this.level().purpurConfig.traderLlamaJumpStrengthMin, this.level().purpurConfig.traderLlamaJumpStrengthMax);
    }

    @Override
    public double generateSpeed(net.minecraft.util.RandomSource random) {
        return generateSpeed(this.level().purpurConfig.traderLlamaMovementSpeedMin, this.level().purpurConfig.traderLlamaMovementSpeedMax);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Make entity breeding times configurable
    @Override
    public int getPurpurBreedTime() {
        return this.level().purpurConfig.traderLlamaBreedingTicks;
    }
    // Purpur end - Make entity breeding times configurable

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.traderLlamaTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.traderLlamaAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    public boolean isTraderLlama() {
        return true;
    }

    @Override
    protected @Nullable Llama makeNewLlama() {
        TraderLlama baby = EntityType.TRADER_LLAMA.create(this.level(), EntitySpawnReason.BREEDING);
        if (baby != null) {
            baby.setPersistenceRequired();
        }

        return baby;
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("DespawnDelay", this.despawnDelay);
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        this.despawnDelay = input.getIntOr("DespawnDelay", 47999);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new PanicGoal(this, 2.0));
        this.targetSelector.addGoal(1, new TraderLlama.TraderLlamaDefendWanderingTraderGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Zombie.class, true, (target, level) -> !target.is(EntityType.ZOMBIFIED_PIGLIN)));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, AbstractIllager.class, true));
    }

    public void setDespawnDelay(final int despawnDelay) {
        this.despawnDelay = despawnDelay;
    }

    @Override
    protected void doPlayerRide(final Player player) {
        Entity leashHolder = this.getLeashHolder();
        if (!(leashHolder instanceof WanderingTrader)) {
            super.doPlayerRide(player);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide()) {
            this.maybeDespawn();
        }
    }

    private void maybeDespawn() {
        if (this.canDespawn()) {
            this.despawnDelay = this.isLeashedToWanderingTrader() ? ((WanderingTrader)this.getLeashHolder()).getDespawnDelay() - 1 : this.despawnDelay - 1;
            if (this.despawnDelay <= 0) {
                this.removeLeash();
                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
            }
        }
    }

    private boolean canDespawn() {
        return !this.isTamed()
            && !this.isLeashedToSomethingOtherThanTheWanderingTrader()
            && !this.hasExactlyOnePlayerPassenger()
            && !this.isAgeLocked()
            && !this.isPersistenceRequired();
    }

    private boolean isLeashedToWanderingTrader() {
        return this.getLeashHolder() instanceof WanderingTrader;
    }

    private boolean isLeashedToSomethingOtherThanTheWanderingTrader() {
        return this.isLeashed() && !this.isLeashedToWanderingTrader();
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level, final DifficultyInstance difficulty, final EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData
    ) {
        if (spawnReason == EntitySpawnReason.EVENT) {
            this.setAge(0);
        }

        if (groupData == null) {
            groupData = new AgeableMob.AgeableMobGroupData(false);
        }

        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    protected static class TraderLlamaDefendWanderingTraderGoal extends TargetGoal {
        private final Llama llama;
        private LivingEntity ownerLastHurtBy;
        private int timestamp;

        public TraderLlamaDefendWanderingTraderGoal(final Llama tameAnimal) {
            super(tameAnimal, false);
            this.llama = tameAnimal;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!this.llama.isLeashed()) {
                return false;
            } else if (!(this.llama.getLeashHolder() instanceof WanderingTrader owner)) {
                return false;
            } else {
                this.ownerLastHurtBy = owner.getLastHurtByMob();
                int timeStamp = owner.getLastHurtByMobTimestamp();
                return timeStamp != this.timestamp && this.canAttack(this.ownerLastHurtBy, TargetingConditions.DEFAULT);
            }
        }

        @Override
        public void start() {
            this.mob.setTarget(this.ownerLastHurtBy, org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_ATTACKED_OWNER); // CraftBukkit
            Entity leashHolder = this.llama.getLeashHolder();
            if (leashHolder instanceof WanderingTrader) {
                this.timestamp = ((WanderingTrader)leashHolder).getLastHurtByMobTimestamp();
            }

            super.start();
        }
    }
}
