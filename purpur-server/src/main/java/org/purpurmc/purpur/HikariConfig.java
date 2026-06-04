package org.purpurmc.purpur;

import org.bukkit.Bukkit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.logging.Level;

/**
 * Hikari server configuration — loaded from purpur.yml under settings.hikari.*
 */
public class HikariConfig {

    // Multithreaded Dimension Ticking
    public static boolean mdtEnabled = true;
    public static int mdtThreads = 0; // 0 = auto-detect (number of dimensions, capped at CPU cores)

    // Async Chunk Sending
    public static boolean asyncChunkSending = true;
    public static int asyncChunkThreads = 2;

    // Skip Empty Listeners
    public static boolean skipEmptyListeners = true;

    // Debug Logging
    public static boolean debugLogging = false;

    /**
     * Called from PurpurConfig.readConfig() to load Hikari settings.
     */
    static void init() {
        for (Method method : HikariConfig.class.getDeclaredMethods()) {
            if (Modifier.isPrivate(method.getModifiers())) {
                if (method.getParameterTypes().length == 0 && method.getReturnType() == Void.TYPE) {
                    try {
                        method.setAccessible(true);
                        method.invoke(null);
                    } catch (InvocationTargetException ex) {
                        throw new RuntimeException(ex.getCause());
                    } catch (Exception ex) {
                        Bukkit.getLogger().log(Level.SEVERE, "Error invoking Hikari config method: " + method, ex);
                    }
                }
            }
        }
    }

    private static void hikariSettings() {
        mdtEnabled = getBoolean("settings.hikari.mdt-enabled", mdtEnabled);
        mdtThreads = getInt("settings.hikari.mdt-threads", mdtThreads);
        asyncChunkSending = getBoolean("settings.hikari.async-chunk-sending", asyncChunkSending);
        asyncChunkThreads = getInt("settings.hikari.async-chunk-threads", asyncChunkThreads);
        skipEmptyListeners = getBoolean("settings.hikari.skip-empty-listeners", skipEmptyListeners);
        debugLogging = getBoolean("settings.hikari.debug-logging", debugLogging);

        // Validate
        if (mdtThreads < 0) {
            Bukkit.getLogger().warning("[Hikari] mdt-threads cannot be negative, resetting to 0 (auto)");
            mdtThreads = 0;
        }
        if (asyncChunkThreads < 1) {
            Bukkit.getLogger().warning("[Hikari] async-chunk-threads must be >= 1, resetting to 2");
            asyncChunkThreads = 2;
        }

        // Initialize logger state
        HikariLogger.setEnabled(debugLogging);

        // Print TUI-style config summary
        java.util.Map<String, String> configValues = new java.util.LinkedHashMap<>();
        configValues.put("MDT Enabled", mdtEnabled ? "✔ YES" : "✖ NO");
        configValues.put("MDT Threads", mdtThreads == 0 ? "auto" : String.valueOf(mdtThreads));
        configValues.put("Async Chunk Sending", asyncChunkSending ? "✔ YES" : "✖ NO");
        configValues.put("Async Chunk Threads", String.valueOf(asyncChunkThreads));
        configValues.put("Skip Empty Listeners", skipEmptyListeners ? "✔ YES" : "✖ NO");
        configValues.put("Debug Logging", debugLogging ? "✔ YES" : "✖ NO");
        HikariLogger.printConfigSummary(configValues);
    }

    // Delegate to PurpurConfig helpers (addDefault + get pattern)
    private static boolean getBoolean(String path, boolean def) {
        PurpurConfig.config.addDefault(path, def);
        return PurpurConfig.config.getBoolean(path, PurpurConfig.config.getBoolean(path));
    }

    private static int getInt(String path, int def) {
        PurpurConfig.config.addDefault(path, def);
        return PurpurConfig.config.getInt(path, PurpurConfig.config.getInt(path));
    }

    private static void log(String s) {
        PurpurConfig.log(Level.INFO, s);
    }
}
