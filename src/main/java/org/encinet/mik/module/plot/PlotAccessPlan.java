package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

record PlotAccessPlan(Plot before, Plot after, Plot parent, List<Plot> children,
                      PlotAccessRequest request, List<Effect> effects) {
    record Effect(UUID plot, PlotAccessPolicy.Subject subject, PlotPermission permission,
                  boolean before, boolean after) { }

    PlotAccessPlan {
        children = List.copyOf(children);
        effects = List.copyOf(effects);
    }

    static PlotAccessPlan prepare(Plot plot, Plot parent, List<Plot> children, PlotAccessRequest request) {
        Objects.requireNonNull(request);
        Map<UUID, Plot.Role> members = new HashMap<>(plot.members());
        Map<String, Boolean> flags = plot.flags();
        if (request instanceof PlotAccessRequest.Member member) {
            if (member.player().equals(plot.owner())) throw new PlotProblem(Message.PLOT_ERROR_OWNER_MEMBER);
            if (parent != null && member.player().equals(parent.owner()))
                throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_SUPERVISOR_MEMBER);
            members.put(member.player(), member.role());
            flags = PlotAccessPolicy.withoutSubject(flags, member.subject(), null);
            for (var override : member.overrides().entrySet()) {
                if (!override.getKey().appliesTo(plot)) throw new PlotProblem(Message.PLOT_ERROR_FLAG);
                flags = PlotAccessPolicy.set(flags, member.subject(), override.getKey().key(), override.getValue());
            }
        } else if (request instanceof PlotAccessRequest.Remove remove) {
            requireMember(plot, remove.subject());
            members.remove(remove.player());
            flags = PlotAccessPolicy.withoutSubject(flags, remove.subject(), null);
        } else if (request instanceof PlotAccessRequest.Permission permission) {
            requireMember(plot, permission.subject());
            if (!permission.permission().appliesTo(plot)
                    || !PlotAccessPolicy.editable(permission.subject(), permission.permission().key()))
                throw new PlotProblem(Message.PLOT_ERROR_FLAG);
            flags = PlotAccessPolicy.set(flags, permission.subject(), permission.permission().key(), permission.allowed());
        } else if (request instanceof PlotAccessRequest.Reset reset) {
            if (reset.subject() == null) flags = PlotAccessPolicy.withoutOverrides(flags);
            else {
                requireMember(plot, reset.subject());
                flags = PlotAccessPolicy.withoutSubject(flags, reset.subject(), reset.category());
            }
        }
        Plot next = new Plot(plot.id(), plot.world(), plot.owner(), plot.name(), plot.publicProject(),
                plot.createdAt(), plot.cells(), members, flags, plot.parentId());
        List<Plot> related = request.subject() == null || request.subject().group() != null ? children : List.of();
        List<Effect> effects = new ArrayList<>();
        collect(effects, plot, next, parent, parent);
        for (Plot child : related) collect(effects, child, child, plot, next);
        return new PlotAccessPlan(plot, next, parent, related, request, effects);
    }

    private static void requireMember(Plot plot, PlotAccessPolicy.Subject subject) {
        if (subject.player() != null && !plot.members().containsKey(subject.player()))
            throw new PlotProblem(Message.PLOT_ERROR_MEMBER_MISSING);
    }

    private static void collect(List<Effect> effects, Plot before, Plot after, Plot beforeParent, Plot afterParent) {
        for (PlotAccessPolicy.Group group : PlotAccessPolicy.Group.values()) {
            PlotAccessPolicy.Subject subject = PlotAccessPolicy.Subject.group(group);
            for (PlotPermission permission : PlotPermission.values()) {
                boolean previous = PlotAccessPolicy.view(before, beforeParent, subject, permission.key()).allowed();
                boolean next = PlotAccessPolicy.view(after, afterParent, subject, permission.key()).allowed();
                if (previous != next) effects.add(new Effect(before.id(), subject, permission, previous, next));
            }
        }
        Set<UUID> players = new HashSet<>(before.members().keySet());
        players.addAll(after.members().keySet());
        players.add(before.owner());
        for (UUID player : players) {
            for (PlotPermission permission : PlotPermission.values()) {
                boolean previous = memberAllowed(before, beforeParent, player, permission);
                boolean next = memberAllowed(after, afterParent, player, permission);
                if (previous != next) effects.add(new Effect(before.id(), PlotAccessPolicy.Subject.player(player),
                        permission, previous, next));
            }
        }
    }

    private static boolean memberAllowed(Plot plot, Plot parent, UUID player, PlotPermission permission) {
        return (plot.owner().equals(player) || plot.members().containsKey(player))
                && plot.allows(parent, permission.key(), player, false, false);
    }

    boolean current(PlotRegistry registry) {
        if (!before.equals(registry.byId(before.id())) || !Objects.equals(parent, registry.parentOf(before))) return false;
        return request.subject() != null && request.subject().group() == null
                || children.equals(registry.childrenOf(before.id()));
    }

    boolean requiresConfirmation() {
        return !(request instanceof PlotAccessRequest.Permission permission)
                || permission.permission().category() == PlotPermission.Category.MANAGEMENT;
    }

    Set<PlotPermission> granted() {
        Set<PlotPermission> granted = new HashSet<>();
        for (Effect effect : effects) if (effect.after()) granted.add(effect.permission());
        return Set.copyOf(granted);
    }

    Set<PlotPermission> revoked() {
        Set<PlotPermission> revoked = new HashSet<>();
        for (Effect effect : effects) if (!effect.after()) revoked.add(effect.permission());
        return Set.copyOf(revoked);
    }

    Set<UUID> affectedPlayers() {
        Set<UUID> players = new HashSet<>();
        for (Effect effect : effects) if (effect.subject().player() != null) players.add(effect.subject().player());
        if (request instanceof PlotAccessRequest.Member member) players.add(member.player());
        if (request instanceof PlotAccessRequest.Remove remove) players.add(remove.player());
        return Set.copyOf(players);
    }

    Set<UUID> affectedChildren() {
        Set<UUID> plots = new HashSet<>();
        for (Effect effect : effects) if (!effect.plot().equals(before.id())) plots.add(effect.plot());
        return Set.copyOf(plots);
    }
}
