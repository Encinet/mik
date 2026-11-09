package org.encinet.mik.feature.community.social.game;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.community.chat.model.ChatSender;
import org.encinet.mik.feature.community.chat.model.ChatSubmission;
import org.encinet.mik.feature.community.chat.bridge.ChatIngress;
import org.encinet.mik.feature.community.identity.IdentityBindingManager;
import org.encinet.mik.feature.player.profile.PlayerNameTag;
import org.encinet.mik.feature.player.profile.PlayerNameTagRenderer;
import org.encinet.mik.feature.community.social.chat.SocialChatGateway;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Worker-side identity snapshot followed by main-thread external chat delivery. */
public final class BukkitSocialChatGateway implements SocialChatGateway {
    private final JavaPlugin plugin;
    private final IdentityBindingManager identityBindings;
    private final PlayerNameTagRenderer playerNameTags;
    private volatile ChatIngress ingress;

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

    public synchronized void bind(ChatIngress sink) {
        Objects.requireNonNull(sink, "sink");
        if (ingress != null) {
            throw new IllegalStateException("Social chat ingress is already bound");
        }
        ingress = sink;
    }

    @Override
    public CompletionStage<Void> deliver(ChatSubmission submission) {
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
                            nameTag.prefix(), nameTag.suffix(), nameTag.usernameColor().value());
                }).orElse(original);
        org.encinet.mik.feature.community.chat.model.ChatReferences references =
                enrichMentions(submission.references());
        ChatSubmission enriched = new ChatSubmission(
                submission.id(), submission.origin(), sender,
                submission.conversation(), submission.sourceText(),
                references, submission.receivedAt(),
                submission.processingContext());
        CompletableFuture<Void> delivered = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                ChatIngress sink = ingress;
                if (sink == null) {
                    throw new IllegalStateException(
                            "Social chat ingress has not been bound");
                }
                sink.accept(enriched);
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

    private org.encinet.mik.feature.community.chat.model.ChatReferences enrichMentions(
            org.encinet.mik.feature.community.chat.model.ChatReferences references
    ) {
        java.util.List<org.encinet.mik.feature.community.chat.model.ChatReferences.Mention> mentions =
                references.mentions().stream().map(mention -> identityBindings
                        .find(mention.externalIdentity().key())
                        .map(binding -> mention.withMinecraftTarget(
                                binding.playerId(), binding.playerName()))
                        .orElse(mention)).toList();
        return new org.encinet.mik.feature.community.chat.model.ChatReferences(
                references.replyTo(), references.threadRoot(), mentions);
    }
}
