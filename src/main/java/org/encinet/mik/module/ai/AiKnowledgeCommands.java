package org.encinet.mik.module.ai;

import com.mojang.brigadier.Command;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.ai.knowledge.application.KnowledgeLearningService;
import org.encinet.mik.module.ai.knowledge.application.KnowledgeService;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeDocument;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeScope;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;

/** In-game knowledge curation and player memory administration. */
final class AiKnowledgeCommands {
    private final JavaPlugin plugin;
    private final LanguageService languages;
    private final Supplier<AiRuntimeState> current;

    AiKnowledgeCommands(JavaPlugin plugin, LanguageService languages,
                        Supplier<AiRuntimeState> current) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.languages = Objects.requireNonNull(languages, "languages");
        this.current = Objects.requireNonNull(current, "current");
    }

    int knowledgeStatus(CommandSender sender) {
        AiRuntimeState selected = current.get();
        if (selected == null || selected.knowledge() == null) {
            sendLocalized(sender, NamedTextColor.YELLOW, Message.AI_KNOWLEDGE_DISABLED);
            return 0;
        }
        KnowledgeService.Status status = selected.knowledge().status();
        sendLocalized(sender, NamedTextColor.AQUA, Message.AI_KNOWLEDGE_STATUS,
                status.publicDocuments(), status.publicChunks(),
                status.privateDocuments(), status.privateChunks());
        status.lastSync().ifPresent(value -> sendLocalized(sender, NamedTextColor.GRAY,
                Message.AI_KNOWLEDGE_LAST_SYNC, value));
        if (status.dirty()) {
            sendLocalized(sender, NamedTextColor.YELLOW, Message.AI_KNOWLEDGE_INDEX_DIRTY);
        }
        if (!status.lastFailure().isBlank()) {
            sendLocalized(sender, NamedTextColor.RED, Message.AI_KNOWLEDGE_LAST_FAILURE,
                    status.lastFailure());
        }
        if (selected.learning() != null) {
            KnowledgeLearningService.Status learning = selected.learning().status();
            sendLocalized(sender, NamedTextColor.GRAY, Message.AI_LEARNING_STATUS,
                    learning.persistedCandidates(), learning.activeExtractions(),
                    learning.queuedExtractions(), learning.droppedExtractions(),
                    learning.droppedCandidates(), learning.quarantinedCandidates());
            if (!learning.lastFailure().isBlank()) {
                sendLocalized(sender, NamedTextColor.RED, Message.AI_LEARNING_FAILURE,
                        learning.lastFailure());
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    int knowledgeSync(CommandSender sender) {
        AiRuntimeState selected = current.get();
        if (selected == null || selected.knowledge() == null) {
            sendLocalized(sender, NamedTextColor.YELLOW, Message.AI_KNOWLEDGE_DISABLED);
            return 0;
        }
        sendLocalized(sender, NamedTextColor.GRAY, Message.AI_KNOWLEDGE_SYNC_STARTED);
        selected.workers().submit(selected.knowledge()::sync)
                .whenComplete((result, failure) -> scheduleKnowledgeSyncResult(
                        selected, sender, result, failure));
        return Command.SINGLE_SUCCESS;
    }

    int knowledgeCurate(CommandSender sender) {
        AiRuntimeState selected = current.get();
        if (selected == null || selected.learning() == null) {
            sendLocalized(sender, NamedTextColor.YELLOW, Message.AI_KNOWLEDGE_LEARNING_DISABLED);
            return 0;
        }
        sendLocalized(sender, NamedTextColor.GRAY, Message.AI_KNOWLEDGE_CURATION_STARTED);
        selected.learning().curateNow().whenComplete((result, failure) -> {
            if (!plugin.isEnabled() || current.get() != selected) {
                return;
            }
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (failure != null) {
                        Throwable cause = unwrap(failure);
                        sendLocalized(sender, NamedTextColor.RED,
                                Message.AI_KNOWLEDGE_CURATION_FAILED,
                                Objects.requireNonNullElse(cause.getMessage(),
                                        cause.getClass().getSimpleName()));
                        return;
                    }
                    sendLocalized(sender, NamedTextColor.GREEN,
                            Message.AI_KNOWLEDGE_CURATION_FINISHED,
                            result.applied(), result.skipped(), result.failed(),
                            result.quarantined(), result.processed());
                });
            } catch (RuntimeException ignored) {
                // Plugin shutdown raced with the completion.
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    int knowledgeProtection(
            CommandSender sender,
            String documentId,
            boolean protectedDocument
    ) {
        AiRuntimeState selected = current.get();
        if (selected == null || selected.knowledge() == null) {
            sendLocalized(sender, NamedTextColor.YELLOW, Message.AI_KNOWLEDGE_DISABLED);
            return 0;
        }
        return knowledgeOperation(selected, sender,
                () -> {
                    KnowledgeDocument changed = selected.knowledge().setProtected(
                            KnowledgeScope.PUBLIC, Optional.empty(), documentId,
                            protectedDocument);
                    return OperationResult.success(protectedDocument
                            ? Message.AI_KNOWLEDGE_PROTECTED : Message.AI_KNOWLEDGE_UNPROTECTED,
                            changed.id());
                });
    }

    int knowledgeRollback(
            CommandSender sender,
            String documentId,
            long revision
    ) {
        AiRuntimeState selected = current.get();
        if (selected == null || selected.knowledge() == null) {
            sendLocalized(sender, NamedTextColor.YELLOW, Message.AI_KNOWLEDGE_DISABLED);
            return 0;
        }
        return knowledgeOperation(selected, sender,
                () -> {
                    KnowledgeDocument changed = selected.knowledge().rollback(
                            KnowledgeScope.PUBLIC, Optional.empty(), documentId, revision);
                    return OperationResult.success(Message.AI_KNOWLEDGE_ROLLBACK,
                            changed.id(), changed.revision());
                });
    }

    int memoryList(CommandSender sender, String playerName) {
        AiRuntimeState selected = current.get();
        OfflinePlayer player = knownPlayer(sender, playerName, selected);
        if (player == null) {
            return 0;
        }
        return knowledgeOperation(selected, sender, () -> {
            List<KnowledgeDocument> memories = selected.knowledge()
                    .listUser(player.getUniqueId());
            if (memories.isEmpty()) {
                return OperationResult.success(Message.AI_MEMORY_EMPTY, player.getName());
            }
            return OperationResult.success(Message.AI_MEMORY_LIST, player.getName(),
                    memories.stream().map(value -> value.id() + " (" + value.title() + ")")
                            .collect(java.util.stream.Collectors.joining(", ")));
        });
    }

    int memoryShow(CommandSender sender, String playerName, String documentId) {
        AiRuntimeState selected = current.get();
        OfflinePlayer player = knownPlayer(sender, playerName, selected);
        if (player == null) {
            return 0;
        }
        return knowledgeOperation(selected, sender, () -> {
            Optional<KnowledgeDocument> found = selected.knowledge().repository()
                    .findUser(player.getUniqueId(), documentId);
            if (found.isEmpty()) {
                return OperationResult.failure(Message.AI_MEMORY_NOT_FOUND, documentId);
            }
            KnowledgeDocument memory = found.get();
            String body = memory.body().length() <= 8_000 ? memory.body()
                    : memory.body().substring(0, 8_000) + "\n…";
            return OperationResult.success(Message.AI_MEMORY_SHOW,
                    memory.id(), memory.title() + "\n" + body);
        });
    }

    int memoryForget(CommandSender sender, String playerName, String documentId) {
        AiRuntimeState selected = current.get();
        OfflinePlayer player = knownPlayer(sender, playerName, selected);
        if (player == null) {
            return 0;
        }
        return knowledgeOperation(selected, sender, () -> {
            Optional<String> selectedDocument = documentId.equalsIgnoreCase("all")
                    ? Optional.empty() : Optional.of(documentId);
            int removed = selected.knowledge().forgetUser(
                    player.getUniqueId(), selectedDocument);
            return OperationResult.success(Message.AI_MEMORY_REMOVED,
                    removed, player.getName());
        });
    }

    private OfflinePlayer knownPlayer(
            CommandSender sender,
            String playerName,
            AiRuntimeState selected
    ) {
        if (selected == null || selected.knowledge() == null) {
            sendLocalized(sender, NamedTextColor.YELLOW, Message.AI_KNOWLEDGE_DISABLED);
            return null;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayerIfCached(playerName);
        if (player == null) {
            sendLocalized(sender, NamedTextColor.RED, Message.AI_PLAYER_UNKNOWN, playerName);
        }
        return player;
    }

    private int knowledgeOperation(
            AiRuntimeState selected,
            CommandSender sender,
            Supplier<OperationResult> operation
    ) {
        sendLocalized(sender, NamedTextColor.GRAY, Message.AI_KNOWLEDGE_OPERATION_STARTED);
        selected.workers().submit(operation).whenComplete((result, failure) -> {
            if (!plugin.isEnabled() || current.get() != selected) {
                return;
            }
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (failure == null) {
                        sendLocalized(sender, result.color(), result.message(), result.args());
                    } else {
                        Throwable cause = unwrap(failure);
                        sendLocalized(sender, NamedTextColor.RED,
                                Message.AI_KNOWLEDGE_OPERATION_FAILED,
                                Objects.requireNonNullElse(cause.getMessage(),
                                        cause.getClass().getSimpleName()));
                    }
                });
            } catch (RuntimeException ignored) {
                // Plugin shutdown raced with the completion.
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private void scheduleKnowledgeSyncResult(
            AiRuntimeState selected,
            CommandSender sender,
            KnowledgeService.SyncResult result,
            Throwable failure
    ) {
        if (!plugin.isEnabled() || current.get() != selected) {
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure == null) {
                    sendLocalized(sender, NamedTextColor.GREEN,
                            Message.AI_KNOWLEDGE_SYNCHRONIZED,
                            result.publicDocuments(), result.publicChunks(),
                            result.privateDocuments(), result.privateChunks());
                } else {
                    Throwable cause = unwrap(failure);
                    plugin.getLogger().log(Level.WARNING,
                            "Knowledge synchronization failed", cause);
                    sendLocalized(sender, NamedTextColor.RED,
                            Message.AI_KNOWLEDGE_SYNC_FAILED,
                            Objects.requireNonNullElse(cause.getMessage(),
                                    cause.getClass().getSimpleName()));
                }
            });
        } catch (RuntimeException ignored) {
            // Plugin shutdown raced with the completion.
        }
    }

    private void sendLocalized(
            CommandSender sender,
            NamedTextColor color,
            Message message,
            Object... args
    ) {
        sender.sendMessage(languages.text(language(sender), message, color, args));
    }

    private record OperationResult(Message message, NamedTextColor color, Object[] args) {
        static OperationResult success(Message message, Object... args) {
            return new OperationResult(message, NamedTextColor.GREEN, args);
        }

        static OperationResult failure(Message message, Object... args) {
            return new OperationResult(message, NamedTextColor.RED, args);
        }
    }

    private Language language(CommandSender sender) {
        return sender instanceof Player player
                ? languages.language(player) : Language.DEFAULT;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
