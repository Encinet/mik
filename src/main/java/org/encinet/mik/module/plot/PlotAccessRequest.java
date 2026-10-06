package org.encinet.mik.module.plot;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

sealed interface PlotAccessRequest {
    PlotAccessPolicy.Subject subject();

    record Member(UUID player, Plot.Role role, Map<PlotPermission, Boolean> overrides) implements PlotAccessRequest {
        public Member {
            Objects.requireNonNull(player);
            Objects.requireNonNull(role);
            overrides = Map.copyOf(overrides);
        }

        @Override public PlotAccessPolicy.Subject subject() { return PlotAccessPolicy.Subject.player(player); }
    }

    record Remove(UUID player) implements PlotAccessRequest {
        public Remove { Objects.requireNonNull(player); }
        @Override public PlotAccessPolicy.Subject subject() { return PlotAccessPolicy.Subject.player(player); }
    }

    record Permission(PlotAccessPolicy.Subject subject, PlotPermission permission, Boolean allowed)
            implements PlotAccessRequest {
        public Permission {
            Objects.requireNonNull(subject);
            Objects.requireNonNull(permission);
        }
    }

    record Reset(PlotAccessPolicy.Subject subject, PlotPermission.Category category) implements PlotAccessRequest { }
}
