package org.encinet.mik.integration.mikd;

import org.encinet.mik.protocol.AnnouncementResult;
import org.encinet.mik.protocol.AssistantCommand;
import org.encinet.mik.protocol.AssistantResult;
import org.encinet.mik.protocol.ChatCommand;
import org.encinet.mik.protocol.ChatResult;
import org.encinet.mik.protocol.ConnectionRole;
import org.encinet.mik.protocol.GameQueryExchange;
import org.encinet.mik.protocol.GameQueryWork;
import org.encinet.mik.protocol.GovernanceCommand;
import org.encinet.mik.protocol.GovernanceResult;
import org.encinet.mik.protocol.IdentityCommand;
import org.encinet.mik.protocol.IdentityResult;
import org.encinet.mik.protocol.PaperStateAccepted;
import org.encinet.mik.protocol.PaperStateUpdate;
import org.encinet.mik.protocol.Status;
import org.encinet.mik.protocol.WebLoginApproval;
import org.encinet.mik.protocol.WebLoginReceipt;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;

/** Asynchronous, typed capability; no socket or blocking game-thread calls. */
public interface RequestPort {
    record Diagnostics(Status daemon, List<String> missingWorkflows) {
        public Diagnostics {
            missingWorkflows = List.copyOf(missingWorkflows);
        }
    }

    /** A write was rejected before submission or the daemon explicitly rejected it without committing it. */
    final class NotSubmitted extends IOException {
        private static final long serialVersionUID = 1L;

        public NotSubmitted(String message) {
            super(message);
        }
    }

    static boolean definitelyNotSubmitted(Throwable failure) {
        while (failure instanceof CompletionException || failure instanceof ExecutionException) {
            failure = failure.getCause();
        }
        return failure instanceof NotSubmitted;
    }

    CompletableFuture<Diagnostics> status(ConnectionRole role);

    CompletableFuture<PaperStateAccepted> publish(PaperStateUpdate snapshot);

    default CompletableFuture<ChatResult> chat(ChatCommand command) {
        return chat(command, () -> true);
    }

    CompletableFuture<ChatResult> chat(ChatCommand command, BooleanSupplier authorized);

    CompletableFuture<AssistantResult> assistant(AssistantCommand command);

    CompletableFuture<AnnouncementResult> fetchAnnouncements();

    CompletableFuture<IdentityResult> identity(IdentityCommand command, BooleanSupplier authorized);

    CompletableFuture<GovernanceResult> governance(GovernanceCommand command);

    CompletableFuture<GameQueryWork> gameQuery(GameQueryExchange exchange);

    CompletableFuture<GameQueryWork> accountQuery(GameQueryExchange exchange);

    CompletableFuture<WebLoginReceipt> approveWebLogin(WebLoginApproval approval, BooleanSupplier authorized);
}
