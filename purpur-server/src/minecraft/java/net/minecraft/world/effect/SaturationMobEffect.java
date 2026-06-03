package net.minecraft.world.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

class SaturationMobEffect extends InstantenousMobEffect {
    protected SaturationMobEffect(final MobEffectCategory category, final int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(final ServerLevel level, final LivingEntity mob, final int amplification) {
        if (mob instanceof Player player) {
            // CraftBukkit start
            int oldFoodLevel = player.getFoodData().foodLevel;
            org.bukkit.event.entity.FoodLevelChangeEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callFoodLevelChangeEvent(player, amplification + 1 + oldFoodLevel);
            if (!event.isCancelled()) {
                if (player.level().purpurConfig.playerBurpWhenFull && event.getFoodLevel() == 20 && oldFoodLevel < 20) player.burpDelay = player.level().purpurConfig.playerBurpDelay; // Purpur - Burp delay
                player.getFoodData().eat(event.getFoodLevel() - oldFoodLevel, mob.level().purpurConfig.humanSaturationRegenAmount); // Purpur - Config MobEffect by world
            }

            ((org.bukkit.craftbukkit.entity.CraftPlayer) player.getBukkitEntity()).sendHealthUpdate();
            // CraftBukkit end
        }

        return true;
    }
}
