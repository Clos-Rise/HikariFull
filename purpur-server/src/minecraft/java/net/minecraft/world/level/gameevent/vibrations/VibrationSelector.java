package net.minecraft.world.level.gameevent.vibrations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;

public class VibrationSelector {
    public static final Codec<VibrationSelector> CODEC = RecordCodecBuilder.create(
        i -> i.group(
                VibrationInfo.CODEC.lenientOptionalFieldOf("event").forGetter(o -> o.currentVibrationData.map(Pair::getLeft)),
                Codec.LONG.fieldOf("tick").forGetter(o -> o.currentVibrationData.map(Pair::getRight).orElse(-1L))
            )
            .apply(i, VibrationSelector::new)
    );
    private Optional<Pair<VibrationInfo, Long>> currentVibrationData;

    public VibrationSelector(final Optional<VibrationInfo> currentVibration, final long tick) {
        this.currentVibrationData = currentVibration.map(vibrationInfo -> Pair.of(vibrationInfo, tick));
    }

    public VibrationSelector() {
        this.currentVibrationData = Optional.empty();
    }

    public void addCandidate(final VibrationInfo newVibration, final long tickTime) {
        if (this.shouldReplaceVibration(newVibration, tickTime)) {
            this.currentVibrationData = Optional.of(Pair.of(newVibration, tickTime));
        }
    }

    private boolean shouldReplaceVibration(final VibrationInfo newVibration, final long tickTime) {
        if (this.currentVibrationData.isEmpty()) {
            return true;
        }

        Pair<VibrationInfo, Long> previousData = this.currentVibrationData.get();
        long previousTick = previousData.getRight();
        if (tickTime != previousTick) {
            return false;
        }

        VibrationInfo previousVibration = previousData.getLeft();
        return newVibration.distance() < previousVibration.distance()
            || !(newVibration.distance() > previousVibration.distance())
                && VibrationSystem.getGameEventFrequency(newVibration.gameEvent()) > VibrationSystem.getGameEventFrequency(previousVibration.gameEvent());
    }

    public Optional<VibrationInfo> chosenCandidate(final long time) {
        if (this.currentVibrationData.isEmpty()) {
            return Optional.empty();
        } else {
            return this.currentVibrationData.get().getRight() < time ? Optional.of(this.currentVibrationData.get().getLeft()) : Optional.empty();
        }
    }

    public void startOver() {
        this.currentVibrationData = Optional.empty();
    }
}
