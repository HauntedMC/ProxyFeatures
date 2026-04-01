package nl.hauntedmc.proxyfeatures.framework.log;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import nl.hauntedmc.proxyfeatures.testutil.ComponentLoggerRecorder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureLoggerTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void prefixesAndDelegatesAllLevelsForComponents() {
        ComponentLoggerRecorder recorder = ComponentLoggerRecorder.create();
        FeatureLogger logger = new FeatureLogger(recorder.logger(), "Queue");

        logger.info(Component.text("i"));
        logger.warn(Component.text("w"));
        logger.error(Component.text("e"));
        logger.debug(Component.text("d"));
        logger.trace(Component.text("t"));

        assertEquals("[Queue] i", PLAIN.serialize(firstComponentArg(recorder, "info")));
        assertEquals("[Queue] w", PLAIN.serialize(firstComponentArg(recorder, "warn")));
        assertEquals("[Queue] e", PLAIN.serialize(firstComponentArg(recorder, "error")));
        assertEquals("[Queue] d", PLAIN.serialize(firstComponentArg(recorder, "debug")));
        assertEquals("[Queue] t", PLAIN.serialize(firstComponentArg(recorder, "trace")));
    }

    @Test
    void stringOverloadsDelegateToComponentOverloads() {
        ComponentLoggerRecorder recorder = ComponentLoggerRecorder.create();
        FeatureLogger logger = new FeatureLogger(recorder.logger(), "Queue");

        logger.info("i");
        logger.warn("w");
        logger.error("e");
        logger.debug("d");
        logger.trace("t");

        assertEquals(1, countCalls(recorder, "info"));
        assertEquals(1, countCalls(recorder, "warn"));
        assertEquals(1, countCalls(recorder, "error"));
        assertEquals(1, countCalls(recorder, "debug"));
        assertEquals(1, countCalls(recorder, "trace"));
    }

    private static Component firstComponentArg(ComponentLoggerRecorder recorder, String methodName) {
        return recorder.calls().stream()
                .filter(call -> methodName.equals(call.method()))
                .map(call -> call.args()[0])
                .filter(Component.class::isInstance)
                .map(Component.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + methodName + " call"));
    }

    private static long countCalls(ComponentLoggerRecorder recorder, String methodName) {
        return recorder.calls().stream()
                .filter(call -> methodName.equals(call.method()))
                .count();
    }
}
