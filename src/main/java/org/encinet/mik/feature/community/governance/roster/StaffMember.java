package org.encinet.mik.feature.community.governance.roster;

import java.util.Objects;
import java.time.Instant;
import java.util.UUID;
import org.encinet.mik.shared.role.PlayerRole;

/** Current public responsibilities, independent of online presence or past election results. */
public record StaffMember(UUID id, String name, PlayerRole role, boolean subjectToRemoval,
                          Instant moderatorSince, Instant maintainerSince, String responsibility, boolean grantPending) {
    public StaffMember(UUID id, String name, PlayerRole role, boolean subjectToRemoval) {
        this(id, name, role, subjectToRemoval, null, null, "", false);
    }
    public StaffMember {
        Objects.requireNonNull(id);
        Objects.requireNonNull(role);
        if (name == null || name.isBlank()) name = id.toString();
        if (role != PlayerRole.MODERATOR && role != PlayerRole.CUSTODIAN)
            throw new IllegalArgumentException("Public staff must have a staff role");
    }
}
