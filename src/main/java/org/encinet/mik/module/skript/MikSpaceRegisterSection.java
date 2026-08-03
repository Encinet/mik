package org.encinet.mik.module.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Section;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.util.Kleenean;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.encinet.mik.module.space.SpaceLinkEntrances;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.lang.entry.ContainerEntryData;
import org.skriptlang.skript.lang.entry.EntryContainer;
import org.skriptlang.skript.lang.entry.EntryValidator;
import org.skriptlang.skript.lang.entry.util.ExpressionEntryData;

import java.util.List;
import java.util.Locale;

/** Registers one complete runtime spatial link from a labelled Skript section. */
final class MikSpaceRegisterSection extends Section {

    private final MikSkriptFacade facade;
    private final EntryValidator entries = createEntryValidator();
    private Expression<String> id;
    private Expression<? extends Location> firstCornerA;
    private Expression<? extends Location> firstCornerB;
    private String firstThrough;
    private @Nullable String firstUp;
    private Expression<? extends Location> secondCornerA;
    private Expression<? extends Location> secondCornerB;
    private String secondThrough;
    private @Nullable String secondUp;
    private SpaceLinkEntrances entrances;
    private String owner;

    MikSpaceRegisterSection(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(
            Expression<?>[] expressions,
            int matchedPattern,
            Kleenean delayed,
            SkriptParser.ParseResult parseResult,
            SectionNode sectionNode,
            List<TriggerItem> triggerItems
    ) {
        EntryContainer parsed = entries.validate(sectionNode);
        if (parsed == null) {
            return false;
        }
        EntryContainer first = parsed.getOptional(
                "first surface", EntryContainer.class, false);
        EntryContainer second = parsed.getOptional(
                "second surface", EntryContainer.class, false);
        if (first == null || second == null) {
            return false;
        }

        Expression<? extends Location> parsedFirstCornerA = location(first, "corner a");
        Expression<? extends Location> parsedFirstCornerB = location(first, "corner b");
        Expression<? extends Location> parsedSecondCornerA = location(second, "corner a");
        Expression<? extends Location> parsedSecondCornerB = location(second, "corner b");
        if (parsedFirstCornerA == null || parsedFirstCornerB == null
                || parsedSecondCornerA == null || parsedSecondCornerB == null) {
            return false;
        }

        id = (Expression<String>) expressions[0];
        firstCornerA = parsedFirstCornerA;
        firstCornerB = parsedFirstCornerB;
        firstThrough = first.get("through", String.class, false);
        firstUp = first.getOptional("up", String.class, false);
        secondCornerA = parsedSecondCornerA;
        secondCornerB = parsedSecondCornerB;
        secondThrough = second.get("through", String.class, false);
        secondUp = second.getOptional("up", String.class, false);
        String rawEntrances = parsed.get("entrances", String.class, false);
        entrances = switch (rawEntrances.trim().toLowerCase(Locale.ROOT)) {
            case "first" -> SpaceLinkEntrances.FIRST;
            case "both" -> SpaceLinkEntrances.BOTH;
            default -> null;
        };
        if (entrances == null) {
            Skript.error("MIK space entrances must be first or both");
            return false;
        }
        try {
            MikSkriptSpaceRegistry.validateOrientation(
                    "first surface", firstThrough, firstUp);
            MikSkriptSpaceRegistry.validateOrientation(
                    "second surface", secondThrough, secondUp);
        } catch (IllegalArgumentException exception) {
            Skript.error(exception.getMessage());
            return false;
        }
        owner = MikSkriptOwner.current(getParser());
        return true;
    }

    @Override
    protected @Nullable TriggerItem walk(Event event) {
        String linkId = id.getSingle(event);
        Location firstA = firstCornerA.getSingle(event);
        Location firstB = firstCornerB.getSingle(event);
        Location secondA = secondCornerA.getSingle(event);
        Location secondB = secondCornerB.getSingle(event);
        if (linkId == null || firstA == null || firstB == null
                || secondA == null || secondB == null) {
            return walk(event, false);
        }

        try {
            facade.registerSpace(
                    owner,
                    linkId,
                    firstA,
                    firstB,
                    firstThrough,
                    firstUp,
                    secondA,
                    secondB,
                    secondThrough,
                    secondUp,
                    entrances);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            error("Could not register MIK space: " + exception.getMessage());
        }
        return walk(event, false);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "register MIK space " + (id == null ? "" : id.toString(event, debug));
    }

    static EntryValidator createEntryValidator() {
        return EntryValidator.builder()
                .addEntryData(new ContainerEntryData(
                        "first surface", false, createSurfaceEntryValidator()))
                .addEntryData(new ContainerEntryData(
                        "second surface", false, createSurfaceEntryValidator()))
                .addEntry("entrances", null, false)
                .build();
    }

    private static EntryValidator createSurfaceEntryValidator() {
        return EntryValidator.builder()
                .addEntryData(new ExpressionEntryData<>(
                        "corner a", null, false, Location.class))
                .addEntryData(new ExpressionEntryData<>(
                        "corner b", null, false, Location.class))
                .addEntry("through", null, false)
                .addEntry("up", null, true)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Expression<? extends Location> location(
            EntryContainer entries, String key) {
        return (Expression<? extends Location>) entries.getOptional(
                key, Expression.class, false);
    }
}
