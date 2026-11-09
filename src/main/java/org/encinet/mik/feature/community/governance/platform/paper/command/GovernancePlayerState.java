package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.Objects;
import java.util.UUID;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.ui.UiReadingPosition;

/** Each loading instance owns one request, including repeated reads of the same player. */
sealed interface GovernancePlayerState {
    UUID target();
    UiReadingPosition position();

    record Loading(UUID target,UiReadingPosition position) implements GovernancePlayerState {
        public Loading { Objects.requireNonNull(target); Objects.requireNonNull(position); }
    }
    record Failed(UUID target,UiReadingPosition position,Message reason) implements GovernancePlayerState {
        public Failed { Objects.requireNonNull(target); Objects.requireNonNull(position); Objects.requireNonNull(reason); }
    }
    record Loaded(GovernancePlayerReport.Data data,UiReadingPosition position) implements GovernancePlayerState {
        public Loaded { Objects.requireNonNull(data); Objects.requireNonNull(position); }
        public UUID target() { return data.profile().playerId(); }
    }

    static Loading request(GovernancePlayerState previous,UUID target) {
        return new Loading(target,previous != null && previous.target().equals(target) ? previous.position() : UiReadingPosition.START);
    }

    static GovernancePlayerState complete(GovernancePlayerState current,Loading request,GovernancePlayerReport.Data data) {
        if (current != request) return current;
        if (!request.target().equals(data.profile().playerId())) return new Failed(request.target(),request.position(),Message.GOVERNANCE_OPERATION_ERROR);
        return new Loaded(data,request.position());
    }

    static GovernancePlayerState fail(GovernancePlayerState current,Loading request,Message reason) {
        return current == request ? new Failed(request.target(),request.position(),reason) : current;
    }

    static GovernancePlayerState read(GovernancePlayerState current,Loaded source,UiReadingPosition position) {
        return current == source ? new Loaded(source.data(),position) : current;
    }
}
