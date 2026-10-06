package org.encinet.mik.module.plot;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class PlotAccessPolicy {
    private static final Set<String> BULK = Stream.concat(PlotPermission.Category.BUILD.permissions().stream(),
            Stream.of(PlotPermission.SIGN, PlotPermission.CONTAINER)).map(PlotPermission::key)
            .collect(Collectors.toUnmodifiableSet());

    enum Group {
        OWNER("owner"), ADMIN("admin"), COLLABORATOR("collaborator"), NEWCOMER("newcomer"), MEMBER("member");

        private final String key;
        private final Map<String, String> actionKeys;

        Group(String key) {
            this.key = key;
            Map<String, String> keys = new HashMap<>();
            for (PlotPermission action : PlotPermission.values())
                keys.put(action.key(), key + "." + action.key());
            keys.put("", key + ".");
            actionKeys = Map.copyOf(keys);
        }

        String key(String action) {
            String cached = action == null ? null : actionKeys.get(action);
            return cached == null ? key + "." + action : cached;
        }
    }

    enum Setting {
        DEFAULT, ALLOW, DENY;

        Boolean value() {
            return switch (this) {
                case DEFAULT -> null;
                case ALLOW -> Boolean.TRUE;
                case DENY -> Boolean.FALSE;
            };
        }

        Setting next() {
            return switch (this) {
                case DEFAULT -> ALLOW;
                case ALLOW -> DENY;
                case DENY -> DEFAULT;
            };
        }
    }

    enum Source { LOCAL, PARENT, ROLE, GROUP, OWNER }

    record Decision(boolean allowed, Source source) { }

    record Origin(UUID plot, Group group, Source source) { }

    record View(Setting setting, boolean allowed, Source source, Origin origin) { }

    record Subject(Group group, UUID player) {
        Subject {
            if ((group == null) == (player == null)) throw new IllegalArgumentException("Select one subject");
        }

        static Subject group(Group group) { return new Subject(group, null); }

        static Subject player(UUID player) { return new Subject(null, player); }

        String key(String action) { return player == null ? group.key(action) : playerKey(player, action); }
    }

    private PlotAccessPolicy() { }

    static boolean validAction(String action) {
        return PlotPermission.fromKey(action) != null;
    }

    static boolean validQuery(String action) { return "build".equals(action) || validAction(action); }

    static Setting setting(Map<String, Boolean> flags, Subject subject, String action) {
        Boolean allowed = flags.get(subject.key(action));
        return allowed == null ? Setting.DEFAULT : allowed ? Setting.ALLOW : Setting.DENY;
    }

    static Group actorGroup(Plot plot, UUID actor, boolean member) {
        if (plot.owner().equals(actor)) return Group.OWNER;
        Plot.Role role = plot.members().get(actor);
        if (role == Plot.Role.ADMIN) return Group.ADMIN;
        if (role == Plot.Role.COLLABORATOR) return Group.COLLABORATOR;
        return member ? Group.MEMBER : Group.NEWCOMER;
    }

    static boolean allowed(Plot plot, Plot parent, UUID actor, boolean member, String action) {
        if (!validQuery(action)) return false;
        PlotPermission permission = PlotPermission.fromKey(action);
        if (permission != null && !permission.appliesTo(plot)) return false;
        if (plot.owner().equals(actor) && action.equals(PlotPermission.MANAGE_PERMISSIONS.key())) return true;
        if (action.equals("build")) {
            for (String operation : BULK) if (!allowed(plot, parent, actor, member, operation)) return false;
            return true;
        }
        if (plot.members().containsKey(actor)) {
            Boolean personal = plot.flags().get(playerKey(actor, action));
            if (personal != null) return personal;
        }
        return resolve(plot.flags(), parent == null ? null : parent.flags(),
                actorGroup(plot, actor, member), action).allowed();
    }

    static View view(Plot plot, Plot parent, Group group, String action) {
        return view(plot, parent, Subject.group(group), action);
    }

    static View view(Plot plot, Plot parent, Subject subject, String action) {
        Decision decision = resolveSubject(plot, parent, subject, action);
        Group group = subject.group() == null ? actorGroup(plot, subject.player(), false) : subject.group();
        Decision grouped = resolve(plot.flags(), parent == null ? null : parent.flags(), group, action);
        Origin origin;
        if (decision.source() == Source.LOCAL && subject.player() != null)
            origin = new Origin(plot.id(), null, Source.LOCAL);
        else if (grouped.source() == Source.PARENT)
            origin = new Origin(parent.id(), group, resolve(parent.flags(), null, group, action).source());
        else origin = new Origin(plot.id(), group, grouped.source());
        return new View(setting(plot.flags(), subject, action), decision.allowed(), decision.source(), origin);
    }

    static Map<PlotPermission, Boolean> personalOverrides(Plot plot, UUID player) {
        if (!plot.members().containsKey(player)) return Map.of();
        Map<PlotPermission, Boolean> overrides = new HashMap<>();
        for (PlotPermission permission : PlotPermission.values()) {
            Boolean value = plot.flags().get(playerKey(player, permission.key()));
            if (value != null) overrides.put(permission, value);
        }
        return Map.copyOf(overrides);
    }

    private static Decision resolveSubject(Plot plot, Plot parent, Subject subject, String action) {
        PlotPermission permission = PlotPermission.fromKey(action);
        if (permission != null && !permission.appliesTo(plot)) return new Decision(false, Source.ROLE);
        if (subject.player() != null) {
            Boolean own = plot.members().containsKey(subject.player())
                    ? plot.flags().get(subject.key(action)) : null;
            if (own != null) return new Decision(own, Source.LOCAL);
            Decision fallback = resolve(plot.flags(), parent == null ? null : parent.flags(),
                    actorGroup(plot, subject.player(), false), action);
            return new Decision(fallback.allowed(), Source.GROUP);
        }
        return resolve(plot.flags(), parent == null ? null : parent.flags(), subject.group(), action);
    }

    static Decision resolve(Map<String, Boolean> flags, Map<String, Boolean> parentFlags,
                            Group group, String action) {
        if (!validQuery(action)) return new Decision(false, Source.ROLE);
        if (group == Group.OWNER && action.equals(PlotPermission.MANAGE_PERMISSIONS.key()))
            return new Decision(true, Source.OWNER);
        PlotPermission permission = PlotPermission.fromKey(action);
        boolean management = permission != null && permission.category() == PlotPermission.Category.MANAGEMENT;
        if (management && (group == Group.NEWCOMER || group == Group.MEMBER))
            return new Decision(false, Source.ROLE);
        if (action.equals("build")) {
            for (String operation : BULK) {
                if (!resolve(flags, parentFlags, group, operation).allowed())
                    return new Decision(false, Source.GROUP);
            }
            return new Decision(true, Source.GROUP);
        }
        Boolean own = flags.get(group.key(action));
        if (own != null) return new Decision(own, Source.LOCAL);
        if (parentFlags != null) {
            Decision inherited = resolve(parentFlags, null, group, action);
            return new Decision(inherited.allowed(), Source.PARENT);
        }
        boolean allowed = group == Group.OWNER || group == Group.ADMIN
                && permission != PlotPermission.TRANSFER && permission != PlotPermission.DELETE
                || group == Group.COLLABORATOR && !management;
        return new Decision(allowed, Source.ROLE);
    }

    static boolean editable(Subject subject, String action) {
        PlotPermission permission = PlotPermission.fromKey(action);
        if (permission == null || permission.category() != PlotPermission.Category.MANAGEMENT) return true;
        return subject.group() != Group.NEWCOMER && subject.group() != Group.MEMBER
                && !(subject.group() == Group.OWNER && permission == PlotPermission.MANAGE_PERMISSIONS);
    }

    static boolean hasOverrides(Map<String, Boolean> flags) {
        return flags.keySet().stream().anyMatch(PlotAccessPolicy::accessKey);
    }

    static Map<String, Boolean> withoutOverrides(Map<String, Boolean> flags) {
        Map<String, Boolean> copy = new HashMap<>(flags);
        copy.keySet().removeIf(PlotAccessPolicy::accessKey);
        return Map.copyOf(copy);
    }

    static Map<String, Boolean> withoutSubject(Map<String, Boolean> flags, Subject subject,
                                             PlotPermission.Category category) {
        Map<String, Boolean> copy = new HashMap<>(flags);
        for (PlotPermission action : PlotPermission.values()) {
            if (category == null || action.category() == category) copy.remove(subject.key(action.key()));
        }
        return Map.copyOf(copy);
    }

    static boolean hasSubjectOverrides(Map<String, Boolean> flags, Subject subject,
                                       PlotPermission.Category category) {
        for (PlotPermission action : PlotPermission.values()) {
            if (category != null && action.category() != category) continue;
            if (flags.containsKey(subject.key(action.key()))) return true;
        }
        return false;
    }

    static Map<String, Boolean> set(Map<String, Boolean> flags, Subject subject, String action,
                                   Boolean allowed) {
        Map<String, Boolean> copy = new HashMap<>(flags);
        if (allowed == null) copy.remove(subject.key(action));
        else copy.put(subject.key(action), allowed);
        return Map.copyOf(copy);
    }

    static String playerKey(UUID player, String action) { return "player." + player + "." + action; }

    private static boolean accessKey(String key) {
        for (Group group : Group.values()) {
            String prefix = group.key("");
            if (key.startsWith(prefix) && validAction(key.substring(prefix.length()))) return true;
        }
        if (!key.startsWith("player.") || key.length() < 45 || key.charAt(43) != '.') return false;
        try {
            UUID.fromString(key.substring(7, 43));
            return validAction(key.substring(44));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    static Group group(String key) {
        for (Group group : Group.values()) if (group.key.equals(key)) return group;
        return null;
    }
}
