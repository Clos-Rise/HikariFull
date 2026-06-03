package net.minecraft.world.entity.animal.golem;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class SnowGolem extends AbstractGolem implements RangedAttackMob, Shearable {
    private static final EntityDataAccessor<Byte> DATA_PUMPKIN_ID = SynchedEntityData.defineId(SnowGolem.class, EntityDataSerializers.BYTE);
    private static final byte PUMPKIN_FLAG = 16;
    private static final boolean DEFAULT_PUMPKIN = true;
    private java.util.@Nullable UUID summoner; // Purpur - Summoner API

    public SnowGolem(final EntityType<? extends SnowGolem> type, final Level level) {
        super(type, level);
    }

    // Purpur start - Summoner API
    public java.util.@Nullable UUID getSummoner() {
        return summoner;
    }

    public void setSummoner(java.util.@Nullable UUID summoner) {
        this.summoner = summoner;
    }
    // Purpur end - Summoner API

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.snowGolemRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.snowGolemRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.snowGolemControllable;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.snowGolemMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.snowGolemScale);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.snowGolemAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.goalSelector.addGoal(1, new RangedAttackGoal(this, level().purpurConfig.snowGolemAttackDistance, level().purpurConfig.snowGolemSnowBallMin, level().purpurConfig.snowGolemSnowBallMax, level().purpurConfig.snowGolemSnowBallModifier)); // Purpur - Snow Golem rate of fire config
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0D, 1.0000001E-5F));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false, (target, level) -> target instanceof Enemy));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 4.0).add(Attributes.MOVEMENT_SPEED, 0.2F);
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_PUMPKIN_ID, (byte)16);
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Pumpkin", this.hasPumpkin());
        output.storeNullable("Purpur.Summoner", net.minecraft.core.UUIDUtil.CODEC, getSummoner()); // Purpur - Summoner API
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setPumpkin(input.getBooleanOr("Pumpkin", true));
        this.setSummoner(input.read("Purpur.Summoner", net.minecraft.core.UUIDUtil.CODEC).orElse(null)); // Purpur - Summoner API
    }

    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.snowGolemTakeDamageFromWater; // Purpur - Toggle for water sensitive mob damage
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level() instanceof ServerLevel serverLevel) {
            if (serverLevel.environmentAttributes().getValue(EnvironmentAttributes.SNOW_GOLEM_MELTS, this.position())) {
                this.hurtServer(serverLevel, this.damageSources().onFire().knownCause(org.bukkit.event.entity.EntityDamageEvent.DamageCause.MELTING), 1.0F); // CraftBukkit
            }

            if (!serverLevel.getGameRules().get(GameRules.MOB_GRIEFING, serverLevel.purpurConfig.snowGolemMobGriefingOverride)) { // Purpur - Add mobGriefing override to everything affected
                return;
            }

            if (getRider() != null && this.isControllable() && !level().purpurConfig.snowGolemLeaveTrailWhenRidden) return; // Purpur - don't leave snow trail when being ridden
            BlockState snow = Blocks.SNOW.defaultBlockState();

            for (int i = 0; i < 4; i++) {
                int xx = Mth.floor(this.getX() + (i % 2 * 2 - 1) * 0.25F);
                int yy = Mth.floor(this.getY());
                int zz = Mth.floor(this.getZ() + (i / 2 % 2 * 2 - 1) * 0.25F);
                BlockPos snowPos = new BlockPos(xx, yy, zz);
                if (this.level().getBlockState(snowPos).isAir() && snow.canSurvive(this.level(), snowPos)) {
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this.level(), snowPos, snow, net.minecraft.world.level.block.Block.UPDATE_ALL, this)) continue; // CraftBukkit
                    this.level().gameEvent(GameEvent.BLOCK_PLACE, snowPos, GameEvent.Context.of(this, snow));
                }
            }
        }
    }

    @Override
    public void performRangedAttack(final LivingEntity target, final float power) {
        double xd = target.getX() - this.getX();
        double yd = target.getEyeY() - 1.1F;
        double zd = target.getZ() - this.getZ();
        double yo = Math.sqrt(xd * xd + zd * zd) * 0.2F;
        if (this.level() instanceof ServerLevel serverLevel) {
            ItemStack itemStack = new ItemStack(Items.SNOWBALL);
            Projectile.spawnProjectile(
                new Snowball(serverLevel, this, itemStack),
                serverLevel,
                itemStack,
                projectile -> projectile.shoot(xd, yd + yo - projectile.getY(), zd, 1.6F, 12.0F)
            );
        }

        this.playSound(SoundEvents.SNOW_GOLEM_SHOOT, 1.0F, 0.4F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (itemStack.is(Items.SHEARS) && this.readyForShearing()) {
            if (this.level() instanceof ServerLevel level) {
                // CraftBukkit start
                // Paper start - custom shear drops
                java.util.List<ItemStack> drops = this.generateDefaultDrops(level, itemStack);
                org.bukkit.event.player.PlayerShearEntityEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.handlePlayerShearEntityEvent(player, this, itemStack, hand, drops);
                if (event != null) {
                    if (event.isCancelled()) {
                        return tryRide(player, hand); // Purpur - Ridables
                    }
                    drops = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getDrops());
                    // Paper end - custom shear drops
                }
                // CraftBukkit end
                this.shear(level, SoundSource.PLAYERS, itemStack, drops); // Paper - custom shear drops
                this.gameEvent(GameEvent.SHEAR, player);
                itemStack.hurtAndBreak(1, player, hand.asEquipmentSlot());
            }

            return InteractionResult.SUCCESS;
        // Purpur start - Snowman drop and put back pumpkin
        } else if (level().purpurConfig.snowGolemPutPumpkinBack && !hasPumpkin() && itemStack.getItem() == Blocks.CARVED_PUMPKIN.asItem()) {
            setPumpkin(true);
            if (!player.getAbilities().instabuild) {
                itemStack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        // Purpur end - Snowman drop and put back pumpkin
        } else {
            return tryRide(player, hand); // Purpur - Ridables
        }
    }

    @Override
    public void shear(final ServerLevel level, final SoundSource soundSource, final ItemStack tool) {
        // Paper start - custom shear drops
        this.shear(level, soundSource, tool, this.generateDefaultDrops(level, tool));
    }

    @Override
    public java.util.List<ItemStack> generateDefaultDrops(final ServerLevel level, final ItemStack tool) {
        final java.util.List<ItemStack> drops = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
        this.dropFromShearingLootTable(level, BuiltInLootTables.SHEAR_SNOW_GOLEM, tool, (ignored, stack) -> {
            drops.add(stack);
        });
        return drops;
    }

    @Override
    public void shear(final ServerLevel level, final SoundSource soundSource, final ItemStack tool, final java.util.List<ItemStack> drops) {
        // Paper end - custom shear drops
        level.playSound(null, this, SoundEvents.SNOW_GOLEM_SHEAR, soundSource, 1.0F, 1.0F);
        this.setPumpkin(false);
        // Paper start - custom shear drops
        drops.forEach(drop -> {
            this.forceDrops = true; // CraftBukkit
            this.spawnAtLocation(level, drop, this.getEyeHeight());
            this.forceDrops = false; // CraftBukkit
        });
        // Paper end - custom shear drops
    }

    @Override
    public boolean readyForShearing() {
        return this.isAlive() && this.hasPumpkin();
    }

    public boolean hasPumpkin() {
        return (this.entityData.get(DATA_PUMPKIN_ID) & 16) != 0;
    }

    public void setPumpkin(final boolean pumpkin) {
        byte current = this.entityData.get(DATA_PUMPKIN_ID);
        if (pumpkin) {
            this.entityData.set(DATA_PUMPKIN_ID, (byte)(current | 16));
        } else {
            this.entityData.set(DATA_PUMPKIN_ID, (byte)(current & -17));
        }
    }

    @Override
    public @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.SNOW_GOLEM_AMBIENT;
    }

    @Override
    public @Nullable SoundEvent getHurtSound(final DamageSource source) {
        return SoundEvents.SNOW_GOLEM_HURT;
    }

    @Override
    public @Nullable SoundEvent getDeathSound() {
        return SoundEvents.SNOW_GOLEM_DEATH;
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.75F * this.getEyeHeight(), this.getBbWidth() * 0.4F);
    }
}
