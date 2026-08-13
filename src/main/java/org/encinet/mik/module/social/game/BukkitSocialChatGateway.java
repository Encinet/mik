package org.encinet.mik.module.social.game;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.chat.model.ChatSender;
import org.encinet.mik.module.chat.model.ChatSubmission;
import org.encinet.mik.module.identity.IdentityBindingManager;
import org.encinet.mik.module.player.identity.PlayerNameTag;
import org.encinet.mik.module.player.identity.PlayerNameTagRenderer;
import org.encinet.mik.module.social.api.SocialPlatformDescriptor;
import org.encinet.mik.module.social.chat.SocialChatGateway;
import org.encinet.mik.module.social.chat.SocialChatGameSink;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Worker-side identity snapshot followed by main-thread external chat delivery. */
public final class BukkitSocialChatGateway implements SocialChatGateway {
    private final JavaPlugin plugin;
    private final IdentityBindingManager identityBindings;
    private final PlayerNameTagRenderer playerNameTags;
    private volatile SocialChatGameSink gameSink;

    public BukkitSocialChatGateway(
            JavaPlugin plugin,
            IdentityBindingManager identityBindings,
            PlayerNameTagRenderer playerNameTags
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.identityBindings = Objects.requireNonNull(
                identityBindings, "identityBindings");
        this.playerNameTags = Objects.requireNonNull(
                playerNameTags, "playerNameTags");
    }

    public synchronized void bind(SocialChatGameSink sink) {
        Objects.requireNonNull(sink, "sink");
        if (gameSink != null) {
            throw new IllegalStateException("Social chat game sink is already bound");
        }
        gameSink = sink;
    }

    @Override
    public CompletionStage<Void> deliver(
            SocialPlatformDescriptor platform,
            ChatSubmission submission
    ) {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(submission, "submission");
        ChatSender original = submission.sender();
        ChatSender sender = original.externalIdentity()
                .flatMap(identity -> identityBindings.find(identity.key()))
                .map(binding -> {
                    PlayerNameTag nameTag = playerNameTags.resolve(
                            binding.playerId(), binding.playerName());
                    return new ChatSender(
                            binding.playerName(), Optional.of(binding.playerId()),
                            original.externalIdentity(), binding.playerName(),
                            nameTag.prefix(), nameTag.suffix());
                }).orElse(original);
        org.encinet.mik.module.chat.model.ChatReferences references =
                enrichMentions(submission.references());
        ChatSubmission enriched = new ChatSubmission(
                submission.id(), submission.origin(), sender,
                submission.conversation(), submission.sourceText(),
                references, submission.receivedAt(),
                submission.processingContext());
        CompletableFuture<Void> delivered = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                SocialChatGameSink sink = gameSink;
                if (sink == null) {
                    throw new IllegalStateException(
                            "Social chat game sink has not been bound");
                }
                sink.display(platform, enriched);
                delivered.complete(null);
            } catch (RuntimeException error) {
                delivered.completeExceptionally(error);
            }
        };
        try {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        } catch (RuntimeException error) {
            delivered.completeExceptionally(error);
        }
        return delivered;
    }

    private org.encinet.mik.module.chat.model.ChatReferences enrichMentions(
            org.encinet.mik.module.chat.model.ChatReferences references
    ) {
        java.util.List<org.encinet.mik.module.chat.model.ChatReferences.Mention> mentions =
                references.mentions().stream().map(mention -> identityBindings
                        .find(mention.externalIdentity().key())
                        .map(binding -> mention.withMinecraftTarget(
                                binding.playerId(), binding.playerName()))
                        .orElse(mention)).toList();
        return new org.encinet.mik.module.chat.model.ChatReferences(
                references.replyTo(), references.threadRoot(), mentions);
    }
}
