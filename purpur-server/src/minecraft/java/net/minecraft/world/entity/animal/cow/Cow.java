package net.minecraft.world.entity.animal.cow;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class Cow extends AbstractCow {
    private boolean isNaturallyAggressiveToPlayers; // Purpur - Cows naturally aggressive to players chance

    private static final EntityDataAccessor<Holder<CowVariant>> DATA_VARIANT_ID = SynchedEntityData.defineId(Cow.class, EntityDataSerializers.COW_VARIANT);
    private static final EntityDataAccessor<Holder<CowSoundVariant>> DATA_SOUND_VARIANT_ID = SynchedEntityData.defineId(
        Cow.class, EntityDataSerializers.COW_SOUND_VARIANT
    );

    public Cow(final EntityType<? extends Cow> type, final Level level) {
        super(type, level);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.cowRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.cowRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.cowControllable;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.cowMaxHealth);
        this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE).setBaseValue(this.level().purpurConfig.cowScale);
        this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(this.level().purpurConfig.cowNaturallyAggressiveToPlayersDamage); // Purpur - Cows naturally aggressive to players chance
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Make entity breeding times configurable
    @Override
    public int getPurpurBreedTime() {
        return this.level().purpurConfig.cowBreedingTicks;
    }
    // Purpur end - Make entity breeding times configurable

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.cowTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.cowAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.2000000476837158D, true)); // Purpur - Cows naturally aggressive to players chance
        this.targetSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.player.Player.class, 10, true, false, (ignored, ignored2) -> isNaturallyAggressiveToPlayers)); // Purpur - Cows naturally aggressive to players chance
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        Registry<CowSoundVariant> cowSoundVariants = this.registryAccess().lookupOrThrow(Registries.COW_SOUND_VARIANT);
        entityData.define(DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), CowVariants.TEMPERATE));
        entityData.define(DATA_SOUND_VARIANT_ID, cowSoundVariants.get(CowSoundVariants.CLASSIC).or(cowSoundVariants::getAny).orElseThrow());
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        super.addAdditionalSaveData(output);
        VariantUtils.writeVariant(output, this.getVariant());
        this.getSoundVariant()
            .unwrapKey()
            .ifPresent(
                soundVariant -> output.store("sound_variant", ResourceKey.codec(Registries.COW_SOUND_VARIANT), (ResourceKey<CowSoundVariant>)soundVariant)
            );
    }

    // Purpur start - Cows naturally aggressive to players chance
    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        return AbstractCow.createAttributes().add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, 0.0D);
    }
    // Purpur end - Cows naturally aggressive to players chance

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        VariantUtils.readVariant(input, Registries.COW_VARIANT).ifPresent(this::setVariant);
        input.read("sound_variant", ResourceKey.codec(Registries.COW_SOUND_VARIANT))
            .flatMap(soundVariant -> this.registryAccess().lookupOrThrow(Registries.COW_SOUND_VARIANT).get((ResourceKey<CowSoundVariant>)soundVariant))
            .ifPresent(this::setSoundVariant);
    }

    @Override
    public @Nullable Cow getBreedOffspring(final ServerLevel level, final AgeableMob partner) {
        Cow baby = EntityType.COW.create(level, EntitySpawnReason.BREEDING);
        if (baby != null && partner instanceof Cow partnerCow) {
            baby.setVariant(this.random.nextBoolean() ? this.getVariant() : partnerCow.getVariant());
        }

        return baby;
    }

    @Override
    public SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level, final DifficultyInstance difficulty, final EntitySpawnReason spawnReason, final @Nullable SpawnGroupData groupData
    ) {
        this.isNaturallyAggressiveToPlayers = level.getLevel().purpurConfig.cowNaturallyAggressiveToPlayersChance > 0.0D && random.nextDouble() <= level.getLevel().purpurConfig.cowNaturallyAggressiveToPlayersChance; // Purpur - Cows naturally aggressive to players chance
        VariantUtils.selectVariantToSpawn(SpawnContext.create(level, this.blockPosition()), Registries.COW_VARIANT).ifPresent(this::setVariant);
        this.setSoundVariant(CowSoundVariants.pickRandomSoundVariant(this.registryAccess(), level.getRandom()));
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    public void setVariant(final Holder<CowVariant> variant) {
        this.entityData.set(DATA_VARIANT_ID, variant);
    }

    public Holder<CowVariant> getVariant() {
        return this.entityData.get(DATA_VARIANT_ID);
    }

    public Holder<CowSoundVariant> getSoundVariant() {
        return this.entityData.get(DATA_SOUND_VARIANT_ID);
    }

    public void setSoundVariant(final Holder<CowSoundVariant> soundVariant) {
        this.entityData.set(DATA_SOUND_VARIANT_ID, soundVariant);
    }

    @Override
    protected CowSoundVariant getSoundSet() {
        return this.getSoundVariant().value();
    }

    @Override
    public <T> @Nullable T get(final DataComponentType<? extends T> type) {
        if (type == DataComponents.COW_VARIANT) {
            return castComponentValue((DataComponentType<T>)type, this.getVariant());
        } else {
            return type == DataComponents.COW_SOUND_VARIANT ? castComponentValue((DataComponentType<T>)type, this.getSoundVariant()) : super.get(type);
        }
    }

    @Override
    protected void applyImplicitComponents(final DataComponentGetter components) {
        this.applyImplicitComponentIfPresent(components, DataComponents.COW_VARIANT);
        this.applyImplicitComponentIfPresent(components, DataComponents.COW_SOUND_VARIANT);
        super.applyImplicitComponents(components);
    }

    @Override
    protected <T> boolean applyImplicitComponent(final DataComponentType<T> type, final T value) {
        if (type == DataComponents.COW_VARIANT) {
            this.setVariant(castComponentValue(DataComponents.COW_VARIANT, value));
            return true;
        } else if (type == DataComponents.COW_SOUND_VARIANT) {
            this.setSoundVariant(castComponentValue(DataComponents.COW_SOUND_VARIANT, value));
            return true;
        } else {
            return super.applyImplicitComponent(type, value);
        }
    }
}
