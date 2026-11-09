package org.encinet.mik.feature.world.plot;

import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class PlotProblemTest {
    @Test void localizedFailurePreservesItsArgumentsAcrossCopiesAndSerialization() throws Exception {
        Serializable[] source = {"north", 12};
        var problem = new PlotProblem(Message.PLOT_ERROR_ARGS, source);
        source[0] = "changed";
        problem.args()[1] = 99;
        assertArrayEquals(new Object[]{"north", 12}, problem.args());
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) { output.writeObject(problem); }
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            var restored = (PlotProblem) input.readObject();
            assertEquals(problem.message(), restored.message());
            assertArrayEquals(problem.args(), restored.args());
        }
    }
}
