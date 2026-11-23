package org.encinet.mik.feature.community.chat.contract;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.encinet.mik.feature.community.chat.input.AdventureComponentImporter;
import org.encinet.mik.feature.community.chat.input.AuthorizedChatMarkup;
import org.encinet.mik.feature.community.chat.mention.PlayerMentionIndex.Target;
import org.encinet.mik.feature.community.chat.mention.PlayerMentions;
import org.encinet.mik.feature.community.chat.model.*;
import org.encinet.mik.feature.community.chat.pipeline.ChatProcessor;
import org.encinet.mik.feature.community.chat.render.MinecraftChatContentRenderer;
import org.encinet.mik.protocol.ChatBody;
import org.encinet.mik.protocol.ChatDescription;
import org.encinet.mik.protocol.ChatExternalIdentity;
import org.encinet.mik.protocol.ChatPart;
import org.encinet.mik.protocol.ChatPlayer;
import org.encinet.mik.protocol.ChatPlayerMention;
import org.encinet.mik.protocol.ChatSocialSource;
import org.encinet.mik.protocol.ChatTextStyle;
import org.encinet.mik.protocol.HistoryChannel;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.test.ChatTestMessages;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Freezes real Java pipeline and renderer results; Rust must consume these expectations. */
public final class ChatSemanticFixtures {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final UUID ALICE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final List<Target> PLAYERS = List.of(new Target(ALICE, "Alice"), new Target(BOB, "Bob"));
    private static final ChatProcessor PROCESSOR = new ChatProcessor(new AuthorizedChatMarkup()::parse);

    private ChatSemanticFixtures() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("One new fixture output path is required");
        Files.writeString(Path.of(args[0]), JSON.toJson(corpus()) + "\n", StandardOpenOption.CREATE_NEW);
    }

    static JsonObject corpus() {
        JsonObject root = new JsonObject();
        root.addProperty("format", "mik-java-semantics-v1");
        root.add("knownPlayers", JSON.toJsonTree(PLAYERS));
        JsonArray cases = new JsonArray();
        add(cases, "generic-and-email", "看看 https://example.org/a?b=c&d=e admin@example.org", context(ChatCapability.URL));
        add(cases, "cjk-mentions", "你好@Alice请叫@Bob来一下", context(ChatCapability.PLAYER_MENTION));
        add(cases, "mention-boundaries", "prefix@Alice @AliceLonger @ aLiCe @Aliceé", context(ChatCapability.PLAYER_MENTION));
        add(cases, "url-label-is-not-mention", "https://example.org/@Alice你好 admin@Alice.example", context(ChatCapability.URL, ChatCapability.PLAYER_MENTION));
        add(cases, "broadcast", "大家@ALL看这里 @alliance", context(ChatCapability.BROADCAST_MENTION));
        add(cases, "external-capabilities", "%i @all <red>hello</red>", context(ChatCapability.URL));
        add(cases, "literal-malicious-tags", "<click:run_command:'/op Alice'><img src=x onerror=alert(1)>[image] @Alice",
                context());
        add(cases, "authorized-styles", "<red><bold>hello</bold></red> <gradient:#55D6FF:#C084FC>😀a界</gradient>",
                context(ChatCapability.MINI_MESSAGE));
        add(cases, "authorized-dangerous-tags-remain-literal", "<click:run_command:'/op Alice'>text</click>",
                context(ChatCapability.MINI_MESSAGE));
        var mentionStyle = ChatTextStyle.newBuilder().setColor(0xFFD166).build();
        var bound = routedSocial(socialSource(), ChatBody.newBuilder().addParts(ChatPart.newBuilder().setText("hi "))
                .addParts(ChatPart.newBuilder().setStyle(mentionStyle).setPlayerMention(ChatPlayerMention.newBuilder()
                        .setPlayer(ChatPlayer.newBuilder().setId(ALICE.toString()).setName("Alice"))
                        .setSourceIdentity(ChatExternalIdentity.newBuilder().setPlatform("matrix")
                                .setIssuer("example.org").setSubject("@alice:example.org").setDisplayName("Alice Social")))));
        cases.add(result("native-bound-mention", "hi @Alice", context(ChatCapability.URL), bound.content(), bound.effects()));
        var unbound = routedSocial(socialSource(), ChatBody.newBuilder().addParts(ChatPart.newBuilder().setText("hi "))
                .addParts(ChatPart.newBuilder().setStyle(mentionStyle).setExternalMention(ChatExternalIdentity.newBuilder()
                        .setPlatform("qq").setIssuer("app-1").setScope("group-2").setSubject("ghost-id").setDisplayName("Ghost"))));
        cases.add(result("native-external-mention", "hi @Ghost", context(ChatCapability.URL), unbound.content(), unbound.effects()));
        var reply = routedSocial(socialSource().setReplyToEventId("event-1").setThreadRootEventId("thread-1"),
                ChatBody.newBuilder().addParts(ChatPart.newBuilder().setText("quoted !bind 123456")));
        cases.add(result("reply-and-thread", "quoted !bind 123456", context(), reply.content(), reply.effects()));
        var image = routedSocial(socialSource(), ChatBody.newBuilder().addParts(ChatPart.newBuilder().setText("before "))
                .addParts(imagePart()).addParts(ChatPart.newBuilder().setText(" after")));
        cases.add(result("trusted-image-placeholder", "before [image] after", context(), image.content(), image.effects()));
        add(cases, "literal-placeholder", "before [image] after", context());
        ChatItemSnapshot item = new ChatItemSnapshot("minecraft:diamond", "Diamond", 3, new byte[0]);
        add(cases, "item-and-slot", "%i %0", new ChatProcessingContext(Set.of(ChatCapability.ITEM, ChatCapability.INVENTORY),
                PlayerMentions.empty(), Optional.of(item), Map.of(0, item)));
        add(cases, "length-clipping", "😀".repeat(50) + " tail", context());
        ChatContent gradient = new ChatContent(List.of(new ChatNode.Text("literal <red> & 😀", new ChatStyle(
                0x55D6FF, 0xC084FC, true, false, true, false))));
        cases.add(result("semantic-gradient", null, context(), gradient, Set.of()));
        root.add("chat", cases);
        root.add("audience", audiences());
        root.add("localizedPlaceholders", localized());
        return root;
    }

    private static ChatProcessingContext context(ChatCapability... capabilities) {
        return new ChatProcessingContext(Set.of(capabilities), PlayerMentions.of(PLAYERS), Optional.empty(), Map.of());
    }

    private static ChatSocialSource.Builder socialSource() {
        return ChatSocialSource.newBuilder().setPlatformId("matrix").setPlatformName("Matrix")
                .setColor(0x55FFFF).setConversationId("!room:example.org").setEventId("$event");
    }

    private static ChatMessage routedSocial(ChatSocialSource.Builder source, ChatBody.Builder body) {
        return ChatTestMessages.routedSocial(source, ChatSender.minecraft(ALICE, "Sender"),
                HistoryChannel.HISTORY_PUBLIC, body).message();
    }

    private static ChatPart.Builder imagePart() {
        return ChatPart.newBuilder().setDescription(ChatDescription.newBuilder()
                .setKind(ChatDescription.Kind.IMAGE).setFallback("[image]"));
    }

    private static void add(JsonArray cases, String name, String source, ChatProcessingContext context) {
        var input = new ChatSubmission(new ChatMessageId("fixture-message"), new ChatOrigin.Minecraft(ALICE),
                ChatSender.minecraft(ALICE, "Sender"), ChatConversation.minecraftPublic(), source, Instant.EPOCH, context);
        ChatMessage message = PROCESSOR.process(input);
        cases.add(result(name, source, context, message.content(), message.effects()));
    }

    private static JsonObject result(String name, String source, ChatProcessingContext context, ChatContent content, Set<ChatEffect> messageEffects) {
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        if (source != null) result.addProperty("source", source);
        result.add("capabilities", JSON.toJsonTree(context.capabilities().stream().map(Enum::name).sorted().toList()));
        result.add("nodes", nodes(content));
        result.addProperty("plain", content.plainText());
        JsonArray effects = new JsonArray();
        for (ChatEffect effect : messageEffects) {
            ChatEffect.Mentions mentioned = (ChatEffect.Mentions) effect;
            JsonObject value = new JsonObject();
            value.addProperty("broadcast", mentioned.broadcast());
            value.add("players", JSON.toJsonTree(mentioned.playerIds().stream().map(UUID::toString).sorted().toList()));
            effects.add(value);
        }
        result.add("effects", effects);
        JsonObject rendering = new JsonObject();
        var minecraft = new MinecraftChatContentRenderer().render(content, MinecraftChatContentRenderer.Context.empty());
        rendering.add("minecraftVisibleNodes", nodes(new AdventureComponentImporter().importComponent(minecraft)));
        result.add("rendering", rendering);
        return result;
    }

    private static JsonArray nodes(ChatContent content) {
        JsonArray result = new JsonArray();
        for (ChatNode node : content.nodes()) {
            JsonObject value = new JsonObject();
            value.addProperty("type", node.getClass().getSimpleName());
            value.addProperty("text", node.visibleText());
            value.add("style", JSON.toJsonTree(node.style()));
            switch (node) {
                case ChatNode.Link link -> value.addProperty("target", link.target().toString());
                case ChatNode.PlayerMention mention -> {
                    value.addProperty("playerId", mention.playerId().toString());
                    value.addProperty("playerName", mention.playerName());
                    mention.sourceIdentity().ifPresent(identity -> value.add("sourceIdentity", JSON.toJsonTree(identity)));
                }
                case ChatNode.ExternalMention mention -> value.add("identity", JSON.toJsonTree(mention.identity()));
                case ChatNode.Placeholder placeholder -> value.addProperty("kind", placeholder.kind().name());
                case ChatNode.Item item -> item.item().ifPresent(snapshot -> {
                    value.addProperty("itemKey", snapshot.itemKey());
                    value.addProperty("itemName", snapshot.displayName());
                    value.addProperty("amount", snapshot.amount());
                });
                default -> { }
            }
            result.add(value);
        }
        return result;
    }

    private static JsonArray audiences() {
        JsonArray result = new JsonArray();
        for (ChatChannel channel : ChatChannel.values()) {
            ChatAudience audience = new ChatAudience(channel, channel == ChatChannel.PRIVATE ? Set.of(ALICE) : Set.of());
            JsonObject value = new JsonObject();
            value.addProperty("channel", channel.name());
            value.addProperty("alice", audience.includes(ALICE, false));
            value.addProperty("bob", audience.includes(BOB, false));
            value.addProperty("staffBob", audience.includes(BOB, true));
            result.add(value);
        }
        return result;
    }

    private static JsonObject localized() {
        JsonObject result = new JsonObject();
        var languages = FluentTestSupport.languages();
        ChatContent content = routedSocial(socialSource(), ChatBody.newBuilder()
                .addParts(ChatPart.newBuilder().setText("[image] ")).addParts(imagePart())).content();
        for (Language language : Language.values()) {
            result.add(language.id(), nodes(content.localized(message -> languages.t(language, message))));
        }
        return result;
    }
}
