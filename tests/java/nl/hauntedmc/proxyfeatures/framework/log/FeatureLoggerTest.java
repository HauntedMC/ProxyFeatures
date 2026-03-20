package nl.hauntedmc.proxyfeatures.framework.log;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class FeatureLoggerTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void prefixesAndDelegatesAllLevelsForComponents() {
        ComponentLogger delegate = mock(ComponentLogger.class);
        FeatureLogger logger = new FeatureLogger(delegate, "Queue");

        logger.info(Component.text("i"));
        logger.warn(Component.text("w"));
        logger.error(Component.text("e"));
        logger.debug(Component.text("d"));
        logger.trace(Component.text("t"));

        ArgumentCaptor<Component> info = ArgumentCaptor.forClass(Component.class);
        ArgumentCaptor<Component> warn = ArgumentCaptor.forClass(Component.class);
        ArgumentCaptor<Component> error = ArgumentCaptor.forClass(Component.class);
        ArgumentCaptor<Component> debug = ArgumentCaptor.forClass(Component.class);
        ArgumentCaptor<Component> trace = ArgumentCaptor.forClass(Component.class);

        verify(delegate).info(info.capture());
        verify(delegate).warn(warn.capture());
        verify(delegate).error(error.capture());
        verify(delegate).debug(debug.capture());
        verify(delegate).trace(trace.capture());

        assertEquals("[Queue] i", PLAIN.serialize(info.getValue()));
        assertEquals("[Queue] w", PLAIN.serialize(warn.getValue()));
        assertEquals("[Queue] e", PLAIN.serialize(error.getValue()));
        assertEquals("[Queue] d", PLAIN.serialize(debug.getValue()));
        assertEquals("[Queue] t", PLAIN.serialize(trace.getValue()));
    }

    @Test
    void stringOverloadsDelegateToComponentOverloads() {
        ComponentLogger delegate = mock(ComponentLogger.class);
        FeatureLogger logger = new FeatureLogger(delegate, "Queue");

        logger.info("i");
        logger.warn("w");
        logger.error("e");
        logger.debug("d");
        logger.trace("t");

        verify(delegate, times(1)).info(any(Component.class));
        verify(delegate, times(1)).warn(any(Component.class));
        verify(delegate, times(1)).error(any(Component.class));
        verify(delegate, times(1)).debug(any(Component.class));
        verify(delegate, times(1)).trace(any(Component.class));
    }
}
