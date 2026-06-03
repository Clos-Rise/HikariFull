package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;

public class GameModeCommand {
    public static final PermissionCheck PERMISSION_CHECK = new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER);

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("gamemode")
                .requires(Commands.hasPermission(PERMISSION_CHECK))
                .then(
                    Commands.argument("gamemode", GameModeArgument.gameMode())
                        .executes(c -> setMode(c, Collections.singleton(c.getSource().getPlayerOrException()), GameModeArgument.getGameMode(c, "gamemode")))
                        .then(
                            Commands.argument("target", EntityArgument.players())
                                .executes(c -> setMode(c, EntityArgument.getPlayers(c, "target"), GameModeArgument.getGameMode(c, "gamemode")))
                        )
                )
        );
    }

    private static void logGamemodeChange(final CommandSourceStack source, final ServerPlayer target, final GameType newType) {
        Component mode = Component.translatable("gameMode." + newType.getName());
        if (source.getEntity() == target) {
            source.sendSuccess(() -> Component.translatable("commands.gamemode.success.self", mode), true);
        } else {
            if (source.getLevel().getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK)) {
                target.sendSystemMessage(Component.translatable("gameMode.changed", mode));
            }

            source.sendSuccess(() -> Component.translatable("commands.gamemode.success.other", target.getDisplayName(), mode), true);
        }
    }

    private static int setMode(final CommandContext<CommandSourceStack> context, final Collection<ServerPlayer> players, final GameType type) {
        // Purpur start - Gamemode extra permissions
        if (org.purpurmc.purpur.PurpurConfig.commandGamemodeRequiresPermission) {
            String gamemode = type.getName();
            CommandSourceStack sender = context.getSource();
            if (!sender.testPermission(Permissions.COMMANDS_GAMEMASTER, "minecraft.command.gamemode." + gamemode)) {
                return 0;
            }
            if (sender.getEntity() instanceof ServerPlayer player && (players.size() > 1 || !players.contains(player)) && !sender.testPermission(Permissions.COMMANDS_GAMEMASTER, "minecraft.command.gamemode." + gamemode + ".other")) {
                return 0;
            }
        }
        // Purpur end - Gamemode extra permissions
        int count = 0;

        for (ServerPlayer player : players) {
            if (setGameMode(context.getSource(), player, type)) {
                count++;
            }
        }

        return count;
    }

    public static void setGameMode(final ServerPlayer player, final GameType type) {
        setGameMode(player.createCommandSourceStack(), player, type);
    }

    private static boolean setGameMode(final CommandSourceStack source, final ServerPlayer player, final GameType type) {
        // Paper start - Expand PlayerGameModeChangeEvent
        return setGameMode(source, player, type, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.COMMAND);
    }

    public static boolean setGameMode(final CommandSourceStack source, final ServerPlayer player, final GameType type, final org.bukkit.event.player.PlayerGameModeChangeEvent.Cause cause) {
        org.bukkit.event.player.PlayerGameModeChangeEvent event = player.setGameMode(type, cause, null);
        if (event != null && !event.isCancelled()) {
            // Paper end - Expand PlayerGameModeChangeEvent
            logGamemodeChange(source, player, type);
            return true;
            // Paper start - Expand PlayerGameModeChangeEvent
        } else if (event != null && event.cancelMessage() != null) {
            source.sendSuccess(() -> io.papermc.paper.adventure.PaperAdventure.asVanilla(event.cancelMessage()), true);
            return false;
            // Paper end - Expand PlayerGameModeChangeEvent
        } else {
            return false;
        }
    }
}
