package org.purpurmc.purpur;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hikari debug logging system with TUI-style formatted output.
 * Controlled by /logs_hikari command or settings.hikari.debug-logging config.
 *
 * Features:
 * - Box-drawing formatted output for readability
 * - Per-subsystem timing and counters
 * - Performance metrics collection
 * - Color-coded severity levels
 */
public class HikariLogger {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean enabled = false;

    // Box-drawing characters for TUI output
    private static final String BOX_TOP_LEFT     = "╔";
    private static final String BOX_TOP_RIGHT    = "╗";
    private static final String BOX_BOTTOM_LEFT  = "╚";
    private static final String BOX_BOTTOM_RIGHT = "╝";
    private static final String BOX_HORIZONTAL   = "═";
    private static final String BOX_VERTICAL     = "║";
    private static final String BOX_T_LEFT       = "╠";
    private static final String BOX_T_RIGHT      = "╣";
    private static final String BOX_CROSS        = "╬";
    private static final String ARROW_RIGHT      = "►";
    private static final String BULLET           = "●";
    private static final String DIAMOND          = "◆";
    private static final String CHECKMARK        = "✔";
    private static final String WARNING          = "⚠";
    private static final String CROSS            = "✖";

    // Per-subsystem metrics
    private static final Map<String, SubsystemMetrics> metrics = new ConcurrentHashMap<>();

    /**
     * Per-subsystem performance tracking.
     */
    public static class SubsystemMetrics {
        private final AtomicLong totalCalls = new AtomicLong(0);
        private final AtomicLong totalTimeNanos = new AtomicLong(0);
        private final AtomicLong lastCallNanos = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);
        private final AtomicLong warnCount = new AtomicLong(0);

        public void recordCall(long durationNanos) {
            totalCalls.incrementAndGet();
            totalTimeNanos.addAndGet(durationNanos);
            lastCallNanos.set(durationNanos);
        }

        public void recordError() {
            errorCount.incrementAndGet();
        }

        public void recordWarning() {
            warnCount.incrementAndGet();
        }

        public long getTotalCalls() { return totalCalls.get(); }
        public long getTotalTimeNanos() { return totalTimeNanos.get(); }
        public long getLastCallNanos() { return lastCallNanos.get(); }
        public long getErrorCount() { return errorCount.get(); }
        public long getWarnCount() { return warnCount.get(); }

        public double getAverageTimeMicros() {
            long calls = totalCalls.get();
            return calls == 0 ? 0 : (totalTimeNanos.get() / 1000.0) / calls;
        }
    }

    /**
     * Fast check for hot-path guards. Avoids string formatting when logging is disabled.
     */
    public static boolean isDebugEnabled() {
        return enabled;
    }

    /**
     * Set the debug logging state.
     */
    public static void setEnabled(boolean state) {
        enabled = state;
        if (state) {
            LOGGER.info("");
            LOGGER.info("{}", boxLine(60));
            LOGGER.info("{} {} {}",
                BOX_VERTICAL,
                padRight("HIKARI DEBUG LOGGING ACTIVATED", 56),
                BOX_VERTICAL);
            LOGGER.info("{} {} {}",
                BOX_VERTICAL,
                padRight("Subsystem tracking enabled", 56),
                BOX_VERTICAL);
            LOGGER.info("{} {} {}",
                BOX_VERTICAL,
                padRight("Use /logs_hikari false to disable", 56),
                BOX_VERTICAL);
            LOGGER.info("{}", boxLine(60));
        } else {
            LOGGER.info("[Hikari] Debug logging DISABLED");
            // Print final metrics summary
            printMetricsSummary();
        }
    }

    /**
     * Get the current debug logging state.
     */
    public static boolean isEnabled() {
        return enabled;
    }

    // ========== Debug logging (only when enabled) ==========

    /**
     * Log a debug message with a tag. Only logs if debug mode is enabled.
     */
    public static void debug(String tag, String message) {
        if (enabled) {
            LOGGER.info("[Hikari/{}] {} {}", tag, ARROW_RIGHT, message);
        }
    }

    /**
     * Log a debug message with a tag and formatted arguments.
     */
    public static void debug(String tag, String message, Object... args) {
        if (enabled) {
            LOGGER.info("[Hikari/{}] {} " + message, prepend(tag, args));
        }
    }

    /**
     * Log a timed operation — records duration in subsystem metrics.
     */
    public static void debugTimed(String tag, String operation, long durationNanos) {
        SubsystemMetrics m = metrics.computeIfAbsent(tag, k -> new SubsystemMetrics());
        m.recordCall(durationNanos);
        if (enabled) {
            double micros = durationNanos / 1000.0;
            String timeStr = micros > 1000
                ? String.format(java.util.Locale.ROOT, "%.2fms", micros / 1000.0)
                : String.format(java.util.Locale.ROOT, "%.1fµs", micros);
            LOGGER.info("[Hikari/{}] {} {} ({})", tag, ARROW_RIGHT, operation, timeStr);
        }
    }

    // ========== Always-on logging ==========

    /**
     * Log an info message (always logged, regardless of debug state).
     */
    public static void info(String tag, String message) {
        LOGGER.info("[Hikari/{}] {} {}", tag, CHECKMARK, message);
    }

    /**
     * Log a warning message (always logged).
     */
    public static void warn(String tag, String message) {
        SubsystemMetrics m = metrics.computeIfAbsent(tag, k -> new SubsystemMetrics());
        m.recordWarning();
        LOGGER.warn("[Hikari/{}] {} {}", tag, WARNING, message);
    }

    /**
     * Log an error message (always logged).
     */
    public static void error(String tag, String message) {
        SubsystemMetrics m = metrics.computeIfAbsent(tag, k -> new SubsystemMetrics());
        m.recordError();
        LOGGER.error("[Hikari/{}] {} {}", tag, CROSS, message);
    }

    /**
     * Log an error with exception.
     */
    public static void error(String tag, String message, Throwable t) {
        SubsystemMetrics m = metrics.computeIfAbsent(tag, k -> new SubsystemMetrics());
        m.recordError();
        LOGGER.error("[Hikari/{}] {} {}", tag, CROSS, message, t);
    }

    // ========== Formatted TUI output ==========

    /**
     * Print a formatted startup banner with system information.
     */
    public static void printStartupBanner(String version, int cpuCores, long maxMemoryMB, int dimensions) {
        LOGGER.info("");
        LOGGER.info("{}", boxLine(60));
        LOGGER.info("{} {} {}",
            BOX_VERTICAL,
            padRight("HIKARI SERVER CORE v" + version, 56),
            BOX_VERTICAL);
        LOGGER.info("{}", boxDivider(60));
        LOGGER.info("{} {} {}",
            BOX_VERTICAL,
            padRight("High-Performance Minecraft Server", 56),
            BOX_VERTICAL);
        LOGGER.info("{}", boxDivider(60));
        LOGGER.info("{} {} {}",
            BOX_VERTICAL,
            padRight(BULLET + " CPU Cores:    " + cpuCores, 56),
            BOX_VERTICAL);
        LOGGER.info("{} {} {}",
            BOX_VERTICAL,
            padRight(BULLET + " Max Memory:   " + maxMemoryMB + " MB", 56),
            BOX_VERTICAL);
        LOGGER.info("{} {} {}",
            BOX_VERTICAL,
            padRight(BULLET + " Dimensions:   " + dimensions, 56),
            BOX_VERTICAL);
        LOGGER.info("{}", boxDivider(60));
        LOGGER.info("{} {} {}",
            BOX_VERTICAL,
            padRight(DIAMOND + " Active Optimizations:", 56),
            BOX_VERTICAL);
        LOGGER.info("{} {} {}",
            BOX_VERTICAL,
            padRight("  " + CHECKMARK + " Multithreaded Dimension Ticking (MDT)", 56),
            BOX_VERTICAL);
        LOGGER.info("{} {} {}",
            BOX_VERTICAL,
            padRight("  " + CHECKMARK + " Async Chunk Sending", 56),
            BOX_VERTICAL);
        LOGGER.info("{} {} {}",
            BOX_VERTICAL,
            padRight("  " + CHECKMARK + " Chunk Priority System", 56),
            BOX_VERTICAL);
        LOGGER.info("{} {} {}",
            BOX_VERTICAL,
            padRight("  " + CHECKMARK + " Parallel AI Processing", 56),
            BOX_VERTICAL);
        LOGGER.info("{} {} {}",
            BOX_VERTICAL,
            padRight("  " + CHECKMARK + " Batch Hopper Processing", 56),
            BOX_VERTICAL);
        LOGGER.info("{} {} {}",
            BOX_VERTICAL,
            padRight("  " + CHECKMARK + " Stationary Entity Throttle", 56),
            BOX_VERTICAL);
        LOGGER.info("{} {} {}",
            BOX_VERTICAL,
            padRight("  " + CHECKMARK + " Alternate Current Redstone", 56),
            BOX_VERTICAL);
        LOGGER.info("{}", boxLine(60));
        LOGGER.info("");
    }

    /**
     * Print a formatted configuration summary.
     */
    public static void printConfigSummary(Map<String, String> configValues) {
        LOGGER.info("{}", boxLine(60));
        LOGGER.info("{} {} {}",
            BOX_VERTICAL,
            padRight("CONFIGURATION", 56),
            BOX_VERTICAL);
        LOGGER.info("{}", boxDivider(60));
        for (Map.Entry<String, String> entry : configValues.entrySet()) {
            LOGGER.info("{} {} {} {}",
                BOX_VERTICAL,
                padRight("  " + entry.getKey(), 30),
                padRight(entry.getValue(), 24),
                BOX_VERTICAL);
        }
        LOGGER.info("{}", boxLine(60));
    }

    /**
     * Print a formatted metrics summary for all tracked subsystems.
     */
    public static void printMetricsSummary() {
        if (metrics.isEmpty()) {
            return;
        }
        LOGGER.info("");
        LOGGER.info("{}", boxLine(70));
        LOGGER.info("{} {} {}",
            BOX_VERTICAL,
            padRight("HIKARI PERFORMANCE METRICS", 66),
            BOX_VERTICAL);
        LOGGER.info("{}", boxDivider(70));
        LOGGER.info("{} {} {}",
            BOX_VERTICAL,
            padRight(String.format("  %-14s %8s %12s %8s %6s",
                "Subsystem", "Calls", "Avg Time", "Errors", "Warns"), 66),
            BOX_VERTICAL);
        LOGGER.info("{}", boxDivider(70));

        for (Map.Entry<String, SubsystemMetrics> entry : metrics.entrySet()) {
            SubsystemMetrics m = entry.getValue();
            String avgTime;
            double avgMicros = m.getAverageTimeMicros();
            if (avgMicros > 1000) {
                avgTime = String.format(java.util.Locale.ROOT, "%.2fms", avgMicros / 1000.0);
            } else {
                avgTime = String.format(java.util.Locale.ROOT, "%.1fµs", avgMicros);
            }

            LOGGER.info("{} {} {}",
                BOX_VERTICAL,
                padRight(String.format("  %-14s %8d %12s %8d %6d",
                    entry.getKey(), m.getTotalCalls(), avgTime, m.getErrorCount(), m.getWarnCount()), 66),
                BOX_VERTICAL);
        }
        LOGGER.info("{}", boxLine(70));
        LOGGER.info("");
    }

    /**
     * Print a section header.
     */
    public static void printSection(String title) {
        LOGGER.info("");
        LOGGER.info("{}", boxLine(title.length() + 8));
        LOGGER.info("{} {} {}",
            BOX_VERTICAL,
            padRight(DIAMOND + " " + title, title.length() + 4),
            BOX_VERTICAL);
        LOGGER.info("{}", boxLine(title.length() + 8));
    }

    /**
     * Print a key-value line.
     */
    public static void printKeyValue(String key, String value) {
        LOGGER.info("  {} {} {}", key, ARROW_RIGHT, value);
    }

    // ========== Helper methods ==========

    private static String boxLine(int width) {
        StringBuilder sb = new StringBuilder(width + 2);
        sb.append(BOX_TOP_LEFT);
        for (int i = 0; i < width; i++) {
            sb.append(BOX_HORIZONTAL);
        }
        sb.append(BOX_TOP_RIGHT);
        return sb.toString();
    }

    private static String boxDivider(int width) {
        StringBuilder sb = new StringBuilder(width + 2);
        sb.append(BOX_T_LEFT);
        for (int i = 0; i < width; i++) {
            sb.append(BOX_HORIZONTAL);
        }
        sb.append(BOX_T_RIGHT);
        return sb.toString();
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width) {
            return s.substring(0, width);
        }
        StringBuilder sb = new StringBuilder(width);
        sb.append(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static Object[] prepend(String tag, Object[] args) {
        Object[] result = new Object[args.length + 2];
        result[0] = tag;
        result[1] = ARROW_RIGHT;
        System.arraycopy(args, 0, result, 2, args.length);
        return result;
    }

    /**
     * Get a snapshot of all metrics for external monitoring.
     */
    public static Map<String, SubsystemMetrics> getMetricsSnapshot() {
        return Map.copyOf(metrics);
    }

    /**
     * Reset all metrics (useful for periodic reporting).
     */
    public static void resetMetrics() {
        metrics.clear();
    }
}
