package org.encinet.mik.integration.mikd;

import org.encinet.mik.protocol.AssistantBlock;
import org.encinet.mik.protocol.AssistantPart;
import org.encinet.mik.protocol.AssistantResult;
import org.encinet.mik.protocol.AssistantSpan.Style;
import org.encinet.mik.protocol.AssistantTable.Alignment;
import org.encinet.mik.protocol.ConnectionRole;
import org.encinet.mik.protocol.Envelope;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MikdFramesTest {
    @Test
    void frozenProtobufAndFramingMatchRustContract() throws IOException {
        byte[] fixture = Files.readAllBytes(Path.of("tests/interop/hello-v1.bin"));
        Envelope message = Envelope.parseFrom(fixture);
        assertEquals("fixture-paper", message.getHello().getGameInstance());
        assertEquals(1, message.getHello().getProtocolVersion());
        assertEquals(ConnectionRole.PAPER_STATE, message.getHello().getRole());
        assertArrayEquals(fixture, message.toByteArray());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MikdFrames.write(Channels.newChannel(output), message);
        byte[] frame = output.toByteArray();
        assertEquals(fixture.length, ByteBuffer.wrap(frame).getInt());
        for (int length = 0; length < frame.length; length++) {
            byte[] truncated = Arrays.copyOf(frame, length);
            assertThrows(IOException.class, () -> MikdFrames.read(Channels.newChannel(new ByteArrayInputStream(truncated))));
        }
        assertEquals(message, MikdFrames.read(Channels.newChannel(new ByteArrayInputStream(frame))));
    }

    @Test
    void assistantV6RichTextHasTheSameWireContentAsRust() throws IOException {
        byte[] fixture = HexFormat.of().parseHex(Files.readString(Path.of("tests/interop/assistant-blocks-v6.hex")).strip());
        var envelope = Envelope.parseFrom(fixture);
        var result = envelope.getResponse().getAssistant();
        assertEquals(AssistantResult.OutcomeCase.REPLY, result.getOutcomeCase());
        var block = result.getReply().getBlocks(1);
        assertEquals(4, result.getReply().getBlocksCount());
        assertEquals(2, result.getReply().getBlocks(0).getHeading().getLevel());
        assertEquals(AssistantBlock.ContentCase.LIST, block.getContentCase());
        var list = block.getList();
        assertEquals(3, list.getFirstNumber());
        assertEquals(1, list.getChildrenCount());
        var first = list.getChildren(0).getBlocks(0).getParagraph();
        assertEquals(3, first.getPartsCount());
        assertEquals("The ", first.getParts(0).getSpans(0).getText());
        assertEquals("Glasshouse", first.getParts(0).getSpans(1).getText());
        assertEquals(List.of(Style.BOLD, Style.ITALIC), first.getParts(0).getSpans(1).getStylesList());
        assertEquals("https://mcmik.top/buildings/0123456789abcdef0123456789abcdef", first.getParts(0).getUrl());
        assertEquals(" · 28 blocks away · ", first.getParts(1).getSpans(0).getText());
        assertEquals(AssistantPart.TargetCase.TARGET_NOT_SET, first.getParts(1).getTargetCase());
        assertEquals("https://mcmik.top/en/wiki?section=visitor-guide", first.getParts(2).getUrl());
        var nested = list.getChildren(0).getBlocks(1).getList().getChildren(0);
        assertTrue(nested.hasChecked());
        assertFalse(nested.getChecked());
        var second = nested.getBlocks(0).getParagraph();
        assertEquals(2, second.getPartsCount());
        assertEquals("Where is the entrance?", second.getParts(0).getSpans(0).getText());
        assertEquals(AssistantPart.TargetCase.ASK_LABEL, second.getParts(0).getTargetCase());
        assertEquals("/plot", second.getParts(1).getSpans(1).getText());
        assertEquals(List.of(Style.CODE), second.getParts(1).getSpans(1).getStylesList());
        assertEquals(AssistantPart.TargetCase.TARGET_NOT_SET, second.getParts(1).getTargetCase());
        var table = result.getReply().getBlocks(2).getTable();
        assertEquals(List.of(Alignment.LEFT, Alignment.RIGHT), table.getColumnsList());
        assertEquals(List.of(Style.STRIKETHROUGH), table.getRows(0).getCells(0).getParts(0).getSpans(0).getStylesList());
        var code = result.getReply().getBlocks(3).getCode();
        assertEquals("sh", code.getLanguage());
        assertEquals("echo first\necho second", code.getText());
        assertEquals(envelope, Envelope.parseFrom(envelope.toByteArray()));
    }

    @Test
    void assistantToolCallMatchesTheSharedFixture() throws IOException {
        byte[] fixture = HexFormat.of().parseHex(Files.readString(Path.of("tests/interop/assistant-tool-call-v9.hex")).strip());
        var envelope = Envelope.parseFrom(fixture);
        var query = envelope.getResponse().getGameQuery().getJob().getAssistantTool();
        assertEquals("00000000-0000-4000-8000-000000000001", query.getPlayerUuid());
        assertEquals("00000000-0000-4000-8000-000000000002", query.getTurnId());
        assertEquals("game_read_weather", query.getName());
        assertEquals("{\"units\":\"seconds\"}", query.getArgumentsJson());
        assertArrayEquals(fixture, envelope.toByteArray());
    }

    @Test
    void assistantAskCarriesPapersDescriptorsInTheSharedFixture() throws IOException {
        byte[] fixture = HexFormat.of().parseHex(Files.readString(Path.of("tests/interop/assistant-tools-ask-v9.hex")).strip());
        var envelope = Envelope.parseFrom(fixture);
        var ask = envelope.getRequest().getAssistant().getAsk();
        assertEquals(1, ask.getGameToolsCount());
        var tool = ask.getGameTools(0);
        assertEquals("game_read_weather", tool.getName());
        assertEquals("Read the requester's weather", tool.getDescription());
        assertEquals("{\"type\":\"object\",\"properties\":{\"units\":{\"type\":\"string\"}}}", tool.getInputSchemaJson());
        assertArrayEquals(fixture, envelope.toByteArray());
    }

    @Test
    void unsignedAndOversizedLengthsFailBeforePayloadRead() {
        for (int length : new int[] {0, -1, MikdFrames.MAX_BYTES + 1}) {
            byte[] prefix = ByteBuffer.allocate(4).putInt(length).array();
            assertThrows(IOException.class, () -> MikdFrames.read(Channels.newChannel(new ByteArrayInputStream(prefix))));
        }
    }
}
