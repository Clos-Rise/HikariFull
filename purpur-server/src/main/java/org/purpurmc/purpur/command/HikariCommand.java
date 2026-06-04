package org.purpurmc.purpur.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import org.purpurmc.purpur.HikariLogger;
import org.purpurmc.purpur.HikariBatchProcessor;
import org.purpurmc.purpur.HikariParallelAI;
import org.purpurmc.purpur.HikariChunkSender;

/**
 * /logs_hikari <true|false> — toggles Hikari debug logging.
 * /logs_hikari metrics — shows performance metrics.
 * /logs_hikari reset — resets metrics counters.
 */
public class HikariCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("logs_hikari")
            .requires(listener -> listener.hasPermission(Permissions.COMMANDS_GAMEMASTER, "bukkit.command.hikari"))
            .then(Commands.argument("enabled", BoolArgumentType.bool())
                .executes(context -> executeToggle(context.getSource(), BoolArgumentType.getBool(context, "enabled")))
            )
            .then(Commands.literal("metrics")
                .executes(context -> executeMetrics(context.getSource()))
            )
            .then(Commands.literal("reset")
                .executes(context -> executeReset(context.getSource()))
            )
            .executes(context -> {
                // Toggle current state if no argument given
                boolean newState = !HikariLogger.isEnabled();
                return executeToggle(context.getSource(), newState);
            })
        );
    }

    private static int executeToggle(CommandSourceStack sender, boolean enabled) {
        HikariLogger.setEnabled(enabled);
        String icon = enabled ? "✔" : "✖";
        String state = enabled ? "ENABLED" : "DISABLED";
        sender.sendSuccess(() -> Component.literal("[Hikari] " + icon + " Debug logging " + state), true);
        return 1;
    }

    private static int executeMetrics(CommandSourceStack sender) {
        var snapshot = HikariLogger.getMetricsSnapshot();

        sender.sendSuccess(() -> Component.literal(""), false);
        sender.sendSuccess(() -> Component.literal("╔═══════════════════════════════════════════════════════════╗"), false);
        sender.sendSuccess(() -> Component.literal("║              HIKARI PERFORMANCE METRICS                  ║"), false);
        sender.sendSuccess(() -> Component.literal("╠═══════════════════════════════════════════════════════════╣"), false);

        // Subsystem timing metrics
        if (!snapshot.isEmpty()) {
            sender.sendSuccess(() -> Component.literal(String.format("║  %-14s %8s %12s %8s %6s     ║",
                "Subsystem", "Calls", "Avg Time", "Errors", "Warns")), false);
            sender.sendSuccess(() -> Component.literal("╠───────────────────────────────────────────────────────────╣"), false);

            for (var entry : snapshot.entrySet()) {
                HikariLogger.SubsystemMetrics m = entry.getValue();
                double avgMicros = m.getAverageTimeMicros();
                String avgTime = avgMicros > 1000
                    ? String.format(java.util.Locale.ROOT, "%.2fms", avgMicros / 1000.0)
                    : String.format(java.util.Locale.ROOT, "%.1fµs", avgMicros);

                sender.sendSuccess(() -> Component.literal(String.format("║  %-14s %8d %12s %8d %6d     ║",
                    entry.getKey(), m.getTotalCalls(), avgTime, m.getErrorCount(), m.getWarnCount())), false);
            }
        } else {
            sender.sendSuccess(() -> Component.literal("║  (No timing metrics yet - run with load to collect)       ║"), false);
        }

        // Entity throttle metrics
        sender.sendSuccess(() -> Component.literal("╠───────────────────────────────────────────────────────────╣"), false);
        sender.sendSuccess(() -> Component.literal("║  ENTITY THROTTLE                                         ║"), false);
        sender.sendSuccess(() -> Component.literal("╠───────────────────────────────────────────────────────────╣"), false);
        long total = net.minecraft.server.level.ServerEntity.getStationaryTotalCount();
        long skipped = net.minecraft.server.level.ServerEntity.getStationarySkippedCount();
        double skipPercent = total > 0 ? (skipped * 100.0 / total) : 0;
        sender.sendSuccess(() -> Component.literal(String.format("║  Total sendChanges:    %,38d  ║", total)), false);
        sender.sendSuccess(() -> Component.literal(String.format("║  Skipped (stationary): %,38d  ║", skipped)), false);
        sender.sendSuccess(() -> Component.literal(String.format("║  Skip rate:            %37.1f%%  ║", skipPercent)), false);

        // Hopper metrics
        sender.sendSuccess(() -> Component.literal("╠───────────────────────────────────────────────────────────╣"), false);
        sender.sendSuccess(() -> Component.literal("║  HOPPERS                                                 ║"), false);
        sender.sendSuccess(() -> Component.literal("╠───────────────────────────────────────────────────────────╣"), false);
        sender.sendSuccess(() -> Component.literal(String.format("║  Total hopper ticks:   %,38d  ║", HikariBatchProcessor.getTotalHopperTicks())), false);

        // Parallel AI metrics
        sender.sendSuccess(() -> Component.literal("╠───────────────────────────────────────────────────────────╣"), false);
        sender.sendSuccess(() -> Component.literal("║  PARALLEL AI                                             ║"), false);
        sender.sendSuccess(() -> Component.literal("╠───────────────────────────────────────────────────────────╣"), false);
        sender.sendSuccess(() -> Component.literal(String.format("║  Executor active:      %-37s  ║", HikariParallelAI.isActive() ? "✔ YES" : "✖ NO")), false);

        // Chunk priority metrics
        sender.sendSuccess(() -> Component.literal("╠───────────────────────────────────────────────────────────╣"), false);
        sender.sendSuccess(() -> Component.literal("║  CHUNK SENDER                                            ║"), false);
        sender.sendSuccess(() -> Component.literal("╠───────────────────────────────────────────────────────────╣"), false);
        sender.sendSuccess(() -> Component.literal(String.format("║  Pending batches:      %,38d  ║", HikariChunkSender.getPendingBatchCount())), false);
        sender.sendSuccess(() -> Component.literal(String.format("║  Active:               %-37s  ║", HikariChunkSender.isActive() ? "✔ YES" : "✖ NO")), false);

        sender.sendSuccess(() -> Component.literal("╚═══════════════════════════════════════════════════════════╝"), false);
        return 1;
    }

    private static int executeReset(CommandSourceStack sender) {
        HikariLogger.resetMetrics();
        HikariBatchProcessor.resetMetrics();
        net.minecraft.server.level.ServerEntity.resetStationaryMetrics();
        sender.sendSuccess(() -> Component.literal("[Hikari] ✔ All metrics counters reset"), true);
        return 1;
    }
}
