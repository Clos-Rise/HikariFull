package net.minecraft.server.jsonrpc.internalapi;

import java.util.stream.Stream;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.jsonrpc.methods.GameRulesService;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;

public class MinecraftGameRuleServiceImpl implements MinecraftGameRuleService {
    private final DedicatedServer server;
    private @org.jspecify.annotations.Nullable GameRules gameRules; // Paper - per-world game rules
    private final JsonRpcLogger jsonrpcLogger;

    public MinecraftGameRuleServiceImpl(final DedicatedServer server, final JsonRpcLogger jsonrpcLogger) {
        this.server = server;
        this.gameRules = null; // Paper - per-world game rules - cannot get game rules until server is started
        this.jsonrpcLogger = jsonrpcLogger;
    }

    // Paper start - per-world game rules
    public GameRules getGameRules() {
        if (this.gameRules == null) {
            this.gameRules = this.server.overworld().getGameRules();
        }
        return this.gameRules;
    }
    // Paper end

    @Override
    public <T> GameRulesService.GameRuleUpdate<T> updateGameRule(final GameRulesService.GameRuleUpdate<T> update, final ClientInfo clientInfo) {
        GameRule<T> gameRule = update.gameRule();
        T oldValue = this.getGameRules().get(gameRule); // Paper - per-world game rules
        T newValue = update.value();
        this.getGameRules().set(gameRule, newValue, this.server.overworld()); // Paper - per-world game rules - use overworld for vanilla protocol
        this.jsonrpcLogger
            .log(clientInfo, "Game rule '{}' updated from '{}' to '{}'", gameRule.id(), gameRule.serialize(oldValue), gameRule.serialize(newValue));
        return update;
    }

    @Override
    public <T> GameRulesService.GameRuleUpdate<T> getTypedRule(final GameRule<T> gameRule, final T value) {
        return new GameRulesService.GameRuleUpdate<>(gameRule, value);
    }

    @Override
    public Stream<GameRule<?>> getAvailableGameRules() {
        return this.getGameRules().availableRules(); // Paper - per-world game rules
    }

    @Override
    public <T> T getRuleValue(final GameRule<T> gameRule) {
        return this.getGameRules().get(gameRule); // Paper - per-world game rules
    }
}
