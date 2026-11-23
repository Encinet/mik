package org.encinet.mik.feature.community.chat.contract;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatSemanticGoldenTest {
    @Test
    void actualPipelineAndRenderersMatchFrozenContract() throws IOException {
        var expected = JsonParser.parseString(Files.readString(Path.of("tests/interop/chat-v1.json")));
        assertEquals(expected, ChatSemanticFixtures.corpus());
    }
}
