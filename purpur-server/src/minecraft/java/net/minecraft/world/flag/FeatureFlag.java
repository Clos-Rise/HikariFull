package net.minecraft.world.flag;

public class FeatureFlag {
    public final FeatureFlagUniverse universe;
    public final long mask;

    FeatureFlag(final FeatureFlagUniverse universe, final int bit) {
        this.universe = universe;
        this.mask = 1L << bit;
    }
}
