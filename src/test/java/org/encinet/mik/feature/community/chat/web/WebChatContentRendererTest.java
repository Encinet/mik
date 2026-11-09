package org.encinet.mik.feature.community.chat.web;

import com.google.gson.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.encinet.mik.feature.community.chat.mention.PlayerMentions;
import org.encinet.mik.feature.community.chat.model.*;
import org.encinet.mik.feature.community.chat.pipeline.AdventureComponentImporter;
import org.encinet.mik.feature.community.chat.pipeline.ChatProcessor;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class WebChatContentRendererTest {
    @Test void projectsMiniMessageStylesWithoutSendingMarkup() {
        ChatProcessingContext context = new ChatProcessingContext(Set.of(ChatCapability.MINI_MESSAGE),
                PlayerMentions.empty(), Optional.empty(), Map.of());
        ChatContent content = new ChatProcessor().process(
                "<red><bold><italic><underlined><strikethrough>hello</strikethrough></underlined></italic></bold></red>", context);
        JsonObject part = WebChatContentRenderer.render(content).get(0).getAsJsonObject();
        assertEquals("hello", part.get("text").getAsString());
        assertEquals(JsonParser.parseString("{\"color\":16733525,\"bold\":true,\"italic\":true,\"underlined\":true,\"strikethrough\":true}"),
                part.get("style"));

        ChatContent gradient = new ChatContent(List.of(new ChatNode.Link("link", URI.create("https://example.org"),
                new ChatStyle(0x112233, 0xAABBCC, false, false, false, false))));
        JsonObject styledLink = WebChatContentRenderer.render(gradient).get(0).getAsJsonObject();
        assertEquals("https://example.org", styledLink.get("href").getAsString());
        assertEquals(JsonParser.parseString("{\"color\":1122867,\"gradientEndColor\":11189196}"), styledLink.get("style"));
    }

    @Test void rainbowKeepsItsColorsAcrossAnOrdinaryLengthMessage() {
        ChatProcessingContext context = new ChatProcessingContext(Set.of(ChatCapability.MINI_MESSAGE),
                PlayerMentions.empty(), Optional.empty(), Map.of());
        ChatContent content = new ChatProcessor().process("<rainbow>" + "x".repeat(230), context);
        JsonArray parts = WebChatContentRenderer.render(content);
        assertEquals(content.plainText(), visible(parts));
        assertTrue(parts.size() > 128 && parts.size() <= 256);
        assertTrue(parts.asList().stream().allMatch(part -> part.getAsJsonObject().has("style")));
    }

    @Test void htmlAndExecutableEventsRemainOnlyVisibleText() {
        String text = "<img src=x onerror=alert(1)><script>alert(1)</script>";
        ChatContent content = new AdventureComponentImporter().importComponent(Component.text(text)
                .clickEvent(ClickEvent.runCommand("/op Bob"))
                .hoverEvent(HoverEvent.showText(Component.text("<svg onload=alert(1)>")))
                .insertion("/op Bob"));
        JsonObject part = WebChatContentRenderer.render(content).get(0).getAsJsonObject();
        assertEquals(Set.of("text"), part.keySet());
        assertEquals(text, part.get("text").getAsString());
    }

    @Test void reusesServiceLabelsAndKeepsTheirActualTargets() {
        ChatContent content = new ChatProcessor().process(
                "https://zh.minecraft.wiki/w/钻石 github.com/encinet/mik BV1GJ411x7h7 admin@example.org",
                ChatProcessingContext.external());
        JsonArray parts = WebChatContentRenderer.render(content);
        assertEquals(content.plainText(), visible(parts));
        List<JsonObject> links = parts.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(part -> part.has("href")).toList();
        assertEquals(4, links.size());
        assertEquals("[Minecraft Wiki: 钻石]", links.getFirst().get("text").getAsString());
        assertEquals("https://zh.minecraft.wiki/w/%E9%92%BB%E7%9F%B3", links.getFirst().get("href").getAsString());
        assertEquals("https://github.com/encinet/mik", links.get(1).get("href").getAsString());
        assertEquals("mailto:admin@example.org", links.getLast().get("href").getAsString());
    }

    @Test void gameOnlyDataAndClickActionsNeverReachTheWire() {
        ChatContent content = new ChatContent(List.of(
                ChatNode.Item.snapshot(new ChatItemSnapshot("minecraft:stone", "Stone", 1,
                        new byte[]{11, 22, 33}), ChatStyle.EMPTY),
                new ChatNode.PlayerMention(UUID.randomUUID(), "Bob", ChatStyle.EMPTY)));
        JsonArray parts = WebChatContentRenderer.render(content);
        assertEquals(content.plainText(), visible(parts));
        assertEquals(Set.of("text"), parts.get(0).getAsJsonObject().keySet());
        assertEquals(Set.of("text", "mention"), parts.get(1).getAsJsonObject().keySet());
        ChatContent imported = new AdventureComponentImporter().importComponent(
                Component.text("run").clickEvent(ClickEvent.runCommand("/op Bob"))
                        .append(Component.text("bad").clickEvent(ClickEvent.openUrl("javascript:alert(1)"))));
        assertEquals("[{\"text\":\"runbad\"}]", WebChatContentRenderer.render(imported).toString());
    }

    @Test void normalizesInternationalHostsAndEmailLocalPartsWithoutAddingMailHeaders() {
        JsonArray parts = WebChatContentRenderer.render(new ChatContent(List.of(
                link("域名", "https://例子.中国:443/页面"),
                link("email", "mailto:a%3Fb%23c+tag@example.org"))));
        assertEquals("https://xn--fsqu00a.xn--fiqs8s:443/%E9%A1%B5%E9%9D%A2", parts.get(0).getAsJsonObject().get("href").getAsString());
        assertEquals("mailto:a%3Fb%23c%2Btag@example.org", parts.get(1).getAsJsonObject().get("href").getAsString());
    }

    @Test void malformedBrowserTargetsDegradeToText() {
        ChatContent content = new ChatContent(List.of(link("bad port ", "https://example.org:70000"),
                link("bad host ", "https://bad_host.example"), link("long", "https://example.org/" + "a".repeat(2048))));
        JsonArray parts = WebChatContentRenderer.render(content);
        assertEquals(1, parts.size());
        assertEquals(Set.of("text"), parts.get(0).getAsJsonObject().keySet());
        assertEquals(content.plainText(), visible(parts));
    }

    @Test void boundsRunCountWithoutDroppingLateMentionsOrAnyVisibleText() {
        UUID mentioned = UUID.randomUUID();
        List<ChatNode> nodes = new ArrayList<>();
        for (int i = 0; i < 400; i++) nodes.add(link("x ", "https://example.org/" + i));
        nodes.add(new ChatNode.PlayerMention(mentioned, "Bob", ChatStyle.EMPTY));
        ChatContent content = new ChatContent(nodes);
        WebChatMessage message = wireMessage(ChatMessageId.random(), null, "Alice", content, null);
        JsonArray parts = message.payload().getAsJsonArray("content");
        assertTrue(parts.size() <= 256);
        assertEquals(content.plainText(), visible(parts));
        assertTrue(message.mentions(mentioned), "Notification selection must not depend on run truncation");
    }

    @Test void evenWorstCaseEscapedContentFitsTheBrowserFrameLimit() {
        List<ChatNode> nodes = new ArrayList<>();
        ChatStyle style = new ChatStyle(0xFFFFFF, 0xAABBCC, true, true, true, true);
        String target = "https://example.org/" + "&".repeat(2028);
        nodes.add(new ChatNode.Link("<", URI.create(target), style));
        nodes.add(new ChatNode.Link("<", URI.create(target), style));
        for (int i = 0; i < 124; i++) nodes.add(new ChatNode.PlayerMention(UUID.randomUUID(), "a", style));
        nodes.add(new ChatNode.Text("<".repeat(16000 - 250), style));
        WebChatMessage message = wireMessage(new ChatMessageId("i".repeat(128)), "r".repeat(64), "<".repeat(64),
                new ChatContent(nodes), ChatSender.minecraft(UUID.randomUUID(), "<".repeat(64)));
        JsonObject payload = message.payload(); payload.addProperty("mentioned", true);
        assertEquals(message.message().content().plainText(), visible(payload.getAsJsonArray("content")));
        JsonObject event = new JsonObject(); event.addProperty("type", "message"); event.add("message", payload);
        assertTrue(new Gson().toJson(event).getBytes(StandardCharsets.UTF_8).length < 128 * 1024);
    }

    private static WebChatMessage wireMessage(ChatMessageId id, String request, String name, ChatContent content, ChatSender target) {
        UUID sender = UUID.randomUUID();
        ChatConversation conversation = target == null ? ChatConversation.minecraftPublic()
                : ChatConversation.minecraftPrivate(sender, target.minecraftId().orElseThrow());
        ChatSubmission submission = new ChatSubmission(id, new ChatOrigin.Minecraft(sender), ChatSender.minecraft(sender, name),
                conversation, "expanded content", ChatReferences.empty(), Instant.EPOCH, ChatProcessingContext.external());
        return new WebChatMessage(new ChatMessage(submission, content, ChatProcessor.effects(content)), request, target);
    }

    private static ChatNode.Link link(String label, String target) { return new ChatNode.Link(label, URI.create(target), ChatStyle.EMPTY); }
    private static String visible(JsonArray parts) { return parts.asList().stream().map(part -> part.getAsJsonObject().get("text").getAsString()).collect(java.util.stream.Collectors.joining()); }
}
