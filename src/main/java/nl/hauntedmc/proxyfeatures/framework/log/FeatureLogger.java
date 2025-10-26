package nl.hauntedmc.proxyfeatures.framework.log;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

/**
 * Delegates to an Adventure ComponentLogger,
 * prefixing every message with [featureName].
 */
public class FeatureLogger {
    private final ComponentLogger delegate;
    private final Component prefix;

    public FeatureLogger(ComponentLogger delegate, String featureName) {
        this.delegate = delegate;
        this.prefix = Component.text("[" + featureName + "] ");
    }

    /**
     * Log at INFO level.
     */
    public void info(Component msg) {
        delegate.info(prefix.append(msg));
    }

    /**
     * Log at WARN level.
     */
    public void warn(Component msg) {
        delegate.warn(prefix.append(msg));
    }

    /**
     * Log at ERROR level.
     */
    public void error(Component msg) {
        delegate.error(prefix.append(msg));
    }

    /**
     * Log at DEBUG level.
     */
    public void debug(Component msg) {
        delegate.debug(prefix.append(msg));
    }

    /**
     * Log at TRACE level.
     */
    public void trace(Component msg) {
        delegate.trace(prefix.append(msg));
    }

    // Optional: convenience overloads if you want to pass Strings directly:
    public void info(String msg) {
        info(Component.text(msg));
    }

    public void warn(String msg) {
        warn(Component.text(msg));
    }

    public void error(String msg) {
        error(Component.text(msg));
    }

    public void debug(String msg) {
        debug(Component.text(msg));
    }

    public void trace(String msg) {
        trace(Component.text(msg));
    }
}
