package org.encinet.mik.feature.community.chat.bridge;

import org.encinet.mik.feature.community.chat.model.ChatDelivery;

import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

/** Publishes an authorized shared copy; persistence does not imply that a client received it. */
@FunctionalInterface
public interface ChatRelay {
    enum Outcome { SAVED, NOT_SUBMITTED, UNCONFIRMED }

    /** Called on the game thread; the authorization check must also be safe on the IPC worker. */
    CompletionStage<Outcome> publish(ChatDelivery delivery, boolean requireWebRecipient, BooleanSupplier authorized);
}
