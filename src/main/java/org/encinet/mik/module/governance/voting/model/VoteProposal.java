package org.encinet.mik.module.governance.voting.model;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, type-specific proposal data carried by the shared voting engine.
 * Adding a vote kind requires a proposal subtype and a matching action executor,
 * but does not duplicate voter-roll, ballot, tally or finalization logic.
 */
public sealed interface VoteProposal permits VoteProposal.Appointment,
        VoteProposal.Removal, VoteProposal.Ban {

    VoteKind kind();

    UUID subjectId();

    String subjectName();

    record Appointment(UUID subjectId, String subjectName) implements VoteProposal {
        public Appointment {
            Objects.requireNonNull(subjectId, "subjectId");
            subjectName = requireText(subjectName, "subjectName");
        }

        @Override
        public VoteKind kind() {
            return VoteKind.APPOINTMENT;
        }
    }

    record Removal(UUID subjectId, String subjectName) implements VoteProposal {
        public Removal {
            Objects.requireNonNull(subjectId, "subjectId");
            subjectName = requireText(subjectName, "subjectName");
        }

        @Override
        public VoteKind kind() {
            return VoteKind.REMOVAL;
        }
    }

    record Ban(
            UUID subjectId,
            String subjectName,
            UUID proposerId,
            String proposerName,
            Duration duration,
            String reason
    ) implements VoteProposal {
        public Ban {
            Objects.requireNonNull(subjectId, "subjectId");
            subjectName = requireText(subjectName, "subjectName");
            Objects.requireNonNull(proposerId, "proposerId");
            proposerName = requireText(proposerName, "proposerName");
            Objects.requireNonNull(duration, "duration");
            if (duration.isZero() || duration.isNegative() || duration.getNano() != 0) {
                throw new IllegalArgumentException(
                        "ban duration must be a positive whole number of seconds");
            }
            reason = requireText(reason, "reason");
        }

        @Override
        public VoteKind kind() {
            return VoteKind.BAN;
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
