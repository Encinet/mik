package org.encinet.mik.module.plot;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

record PlotMemberDraft(UUID player, Plot.Role role, Map<PlotPermission, Boolean> overrides) {
    PlotMemberDraft {
        Objects.requireNonNull(player);
        Objects.requireNonNull(role);
        overrides = Map.copyOf(overrides);
    }

    static PlotMemberDraft of(Plot plot, UUID player, Plot.Role role) {
        return new PlotMemberDraft(player, role, PlotAccessPolicy.personalOverrides(plot, player));
    }

    PlotMemberDraft role(Plot.Role next) { return new PlotMemberDraft(player, next, overrides); }

    PlotMemberDraft cycle(PlotPermission permission) {
        Map<PlotPermission, Boolean> next = new HashMap<>(overrides);
        Boolean current = overrides.get(permission);
        if (current == null) next.put(permission, true);
        else if (current) next.put(permission, false);
        else next.remove(permission);
        return new PlotMemberDraft(player, role, next);
    }

    PlotMemberDraft reset(PlotPermission.Category category) {
        Map<PlotPermission, Boolean> next = new HashMap<>(overrides);
        next.keySet().removeIf(permission -> category == null || permission.category() == category);
        return new PlotMemberDraft(player, role, next);
    }

    PlotAccessRequest.Member request() { return new PlotAccessRequest.Member(player, role, overrides); }
}
