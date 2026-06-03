package net.minecraft.world;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public sealed interface InteractionResult
    permits InteractionResult.Success,
    InteractionResult.Fail,
    InteractionResult.Pass,
    InteractionResult.TryEmptyHandInteraction {
    InteractionResult.Success SUCCESS = new InteractionResult.Success(InteractionResult.SwingSource.CLIENT, InteractionResult.ItemContext.DEFAULT);
    InteractionResult.Success SUCCESS_SERVER = new InteractionResult.Success(InteractionResult.SwingSource.SERVER, InteractionResult.ItemContext.DEFAULT);
    InteractionResult.Success CONSUME = new InteractionResult.Success(InteractionResult.SwingSource.NONE, InteractionResult.ItemContext.DEFAULT);
    InteractionResult.Fail FAIL = new InteractionResult.Fail();
    InteractionResult.Pass PASS = new InteractionResult.Pass();
    InteractionResult.TryEmptyHandInteraction TRY_WITH_EMPTY_HAND = new InteractionResult.TryEmptyHandInteraction();

    default boolean consumesAction() {
        return false;
    }

    record Fail() implements InteractionResult {
    }

    record ItemContext(boolean wasItemInteraction, @Nullable ItemStack heldItemTransformedTo) {
        static final InteractionResult.ItemContext NONE = new InteractionResult.ItemContext(false, null);
        static final InteractionResult.ItemContext DEFAULT = new InteractionResult.ItemContext(true, null);
    }

    record Pass() implements InteractionResult {
    }

    // Paper start - track more context in interaction result
    record PaperSuccessContext(net.minecraft.core.@Nullable BlockPos placedPos) {
        static PaperSuccessContext DEFAULT = new PaperSuccessContext(null);

        public PaperSuccessContext placedBlockAt(final net.minecraft.core.BlockPos pos) {
            return new PaperSuccessContext(pos);
        }
    }
    record Success(InteractionResult.SwingSource swingSource, InteractionResult.ItemContext itemContext, PaperSuccessContext paperSuccessContext) implements InteractionResult {
        public InteractionResult.Success configurePaper(final java.util.function.UnaryOperator<PaperSuccessContext> edit) {
            return new InteractionResult.Success(this.swingSource, this.itemContext, edit.apply(this.paperSuccessContext));
        }

        public Success(final InteractionResult.SwingSource swingSource, final InteractionResult.ItemContext itemContext) {
            this(swingSource, itemContext, PaperSuccessContext.DEFAULT);
        }
        // Paper end - track more context in interaction result
        @Override
        public boolean consumesAction() {
            return true;
        }

        public InteractionResult.Success heldItemTransformedTo(final ItemStack itemStack) {
            return new InteractionResult.Success(this.swingSource, new InteractionResult.ItemContext(true, itemStack), this.paperSuccessContext); // Paper - track more context in interaction result
        }

        public InteractionResult.Success withoutItem() {
            return new InteractionResult.Success(this.swingSource, InteractionResult.ItemContext.NONE, this.paperSuccessContext); // Paper - track more context in interaction result
        }

        public boolean wasItemInteraction() {
            return this.itemContext.wasItemInteraction;
        }

        public @Nullable ItemStack heldItemTransformedTo() {
            return this.itemContext.heldItemTransformedTo;
        }
    }

    enum SwingSource {
        NONE,
        CLIENT,
        SERVER;
    }

    record TryEmptyHandInteraction() implements InteractionResult {
    }
}
