package org.encinet.mik.module.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntConsumer;

/** Immutable, semantic scene description consumed by the floating-menu renderer. */
public final class FloatingMenuDefinition {
    public static final String DEFAULT_REGION = "default";

    private final Component title;
    private final boolean titleVisible;
    private final String screenId;
    private final Map<String, Entry> entries;
    private final Map<String, FloatingMenuDecoration> decorations;
    private final FloatingMenuAnimation animation;
    private final FloatingMenuLayout layout;
    private final FloatingMenuFeedback feedback;
    private final FloatingMenuAppearance appearance;
    private final FloatingMenuFraming framing;
    private final FloatingMenuPresentation presentation;
    private final FloatingMenuAnchorMode anchorMode;
    private final FloatingMenuViewpoint viewpoint;
    private final FloatingMenuMovementPolicy movementPolicy;
    private final Map<FloatingMenuInteraction, FloatingMenuAction> triggers;
    private final FloatingMenuLifecycle lifecycle;
    private final FloatingMenuRefresh refresh;
    private final FloatingMenuFrameObserver frameObserver;

    private FloatingMenuDefinition(Component title, boolean titleVisible,
                                   String screenId, Map<String, Entry> entries,
                                   Map<String, FloatingMenuDecoration> decorations,
                                   FloatingMenuAnimation animation, FloatingMenuLayout layout,
                                   FloatingMenuFeedback feedback, FloatingMenuAppearance appearance,
                                   FloatingMenuFraming framing,
                                   FloatingMenuPresentation presentation,
                                   FloatingMenuAnchorMode anchorMode,
                                   FloatingMenuViewpoint viewpoint,
                                   FloatingMenuMovementPolicy movementPolicy,
                                   Map<FloatingMenuInteraction, FloatingMenuAction> triggers,
                                   FloatingMenuLifecycle lifecycle,
                                   FloatingMenuRefresh refresh,
                                   FloatingMenuFrameObserver frameObserver) {
        this.title = title;
        this.titleVisible = titleVisible;
        this.screenId = screenId;
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        this.decorations = Collections.unmodifiableMap(new LinkedHashMap<>(decorations));
        this.animation = animation;
        this.layout = layout;
        this.feedback = feedback;
        this.appearance = appearance;
        this.framing = framing;
        this.presentation = presentation;
        this.anchorMode = anchorMode;
        this.viewpoint = viewpoint;
        this.movementPolicy = movementPolicy;
        this.triggers = Map.copyOf(triggers);
        this.lifecycle = lifecycle;
        this.refresh = refresh;
        this.frameObserver = frameObserver;
    }

    /** Creates a spatial scene with no fixed capacity or numeric positions. */
    public static Builder builder(Component title) {
        return new Builder(title, true);
    }

    /** Creates a scene with no implicit title node. */
    public static Builder builder() {
        return new Builder(Component.empty(), false);
    }

    /**
     * Creates an untitled application screen with the shared spatial treatment.
     * Prefer this over repeating {@code builder().screen(...).appearance(...)}
     * in feature modules.
     */
    public static Builder screen(String screenId) {
        return builder().screen(screenId).appearance(FloatingMenuAppearance.SPATIAL);
    }

    /** Creates a titled application screen with the shared spatial treatment. */
    public static Builder screen(String screenId, Component title) {
        return builder(title).screen(screenId).appearance(FloatingMenuAppearance.SPATIAL);
    }

    public Component title() { return title; }
    public boolean titleVisible() { return titleVisible; }
    public String screenId() { return screenId; }
    public Map<String, Entry> entries() { return entries; }
    public Map<String, FloatingMenuDecoration> decorations() { return decorations; }
    public FloatingMenuAnimation animation() { return animation; }
    public FloatingMenuLayout layout() { return layout; }
    public FloatingMenuFeedback feedback() { return feedback; }
    public FloatingMenuAppearance appearance() { return appearance; }
    public FloatingMenuFraming framing() { return framing; }
    public FloatingMenuPresentation presentation() { return presentation; }
    public FloatingMenuAnchorMode anchorMode() { return anchorMode; }
    public FloatingMenuViewpoint viewpoint() { return viewpoint; }
    public FloatingMenuMovementPolicy movementPolicy() { return movementPolicy; }
    public Map<FloatingMenuInteraction, FloatingMenuAction> triggers() { return triggers; }
    public FloatingMenuLifecycle lifecycle() { return lifecycle; }
    public FloatingMenuRefresh refresh() { return refresh; }
    public FloatingMenuFrameObserver frameObserver() { return frameObserver; }

    FloatingMenuDefinition identifiedBy(String id) {
        if (screenId != null && !screenId.equals(id)) {
            throw new IllegalArgumentException("Renderer screen id '" + screenId
                    + "' does not match owner '" + id + "'");
        }
        if (id.equals(screenId)) return this;
        return new FloatingMenuDefinition(title, titleVisible, id, entries, decorations,
                animation, layout, feedback, appearance, framing,
                presentation, anchorMode, viewpoint, movementPolicy,
                triggers, lifecycle, refresh, frameObserver);
    }

    FloatingMenuDefinition withLifecycle(FloatingMenuLifecycle nextLifecycle) {
        return new FloatingMenuDefinition(title, titleVisible, screenId, entries, decorations,
                animation, layout, feedback, appearance, framing,
                presentation, anchorMode, viewpoint, movementPolicy,
                triggers, FloatingMenuLifecycle.combine(lifecycle,
                        Objects.requireNonNull(nextLifecycle, "nextLifecycle")), refresh,
                frameObserver);
    }

    /** One independently addressable node in the floating scene. */
    public record Entry(String id, String region, ItemStack item,
                        Component label, FloatingMenuNodeRole role,
                        FloatingMenuTextWidth textWidth,
                        Map<FloatingMenuInteraction, FloatingMenuAction> triggers,
                        FloatingMenuFocusAction focusAction, boolean interactive,
                        FloatingMenuDecoration.Alignment alignment,
                        boolean selected, boolean enabled,
                        Component disabledReason) {
        public Entry {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Element id must not be blank");
            if (region == null || region.isBlank()) throw new IllegalArgumentException("Region must not be blank");
            label = Objects.requireNonNull(label, "label");
            role = Objects.requireNonNull(role, "role");
            textWidth = Objects.requireNonNull(textWidth, "textWidth");
            alignment = Objects.requireNonNull(alignment, "alignment");
            triggers = Map.copyOf(Objects.requireNonNull(triggers, "triggers"));
            item = switch (role.visualStyle()) {
                case TEXT -> {
                    if (item != null) {
                        throw new IllegalArgumentException("Text nodes cannot own item visuals");
                    }
                    yield null;
                }
                case ITEM, BLOCK -> Objects.requireNonNull(item,
                        "Visual elements require an item").clone();
            };
            if (role.visualStyle() == FloatingMenuElementStyle.BLOCK && !item.getType().isBlock()) {
                throw new IllegalArgumentException("BLOCK style requires a block item");
            }
            if (!interactive && (!triggers.isEmpty() || focusAction != null)) {
                throw new IllegalArgumentException("Passive nodes cannot own interaction handlers");
            }
            if (!role.supportsActions() && interactive) {
                throw new IllegalArgumentException("Information nodes cannot own interaction state");
            }
            if (!role.supportsActions() && selected) {
                throw new IllegalArgumentException("Information nodes cannot be selected");
            }
            if (enabled == (disabledReason != null)) {
                throw new IllegalArgumentException(
                        "Disabled nodes require exactly one visible reason");
            }
        }

        @Override
        public ItemStack item() { return item == null ? null : item.clone(); }

        public FloatingMenuElementStyle style() { return role.visualStyle(); }

        public FloatingMenuAction trigger(FloatingMenuInteraction interaction) {
            return triggers.get(interaction);
        }
    }

    public static final class Builder {
        private Component title;
        private boolean titleVisible;
        private String screenId;
        private final Map<String, Entry> entries = new LinkedHashMap<>();
        private final Map<String, FloatingMenuDecoration> decorations = new LinkedHashMap<>();
        private FloatingMenuAnimation animation = FloatingMenuAnimation.DEFAULT;
        private FloatingMenuLayout layout = FloatingMenuLayouts.actions(4);
        private FloatingMenuFeedback feedback = FloatingMenuFeedback.DEFAULT;
        private FloatingMenuAppearance appearance = FloatingMenuAppearance.DEFAULT;
        private FloatingMenuFraming framing = FloatingMenuFraming.COMFORTABLE;
        private FloatingMenuPresentation presentation = FloatingMenuPresentation.ADAPTIVE;
        private FloatingMenuAnchorMode anchorMode = FloatingMenuAnchorMode.ADAPTIVE;
        private FloatingMenuViewpoint viewpoint = FloatingMenuViewpoint.POSE_AWARE;
        private FloatingMenuMovementPolicy movementPolicy =
                FloatingMenuMovementPolicy.STANDARD;
        private FloatingMenuLifecycle lifecycle = FloatingMenuLifecycle.NONE;
        private FloatingMenuRefresh refresh = FloatingMenuRefresh.NONE;
        private FloatingMenuFrameObserver frameObserver =
                FloatingMenuFrameObserver.NONE;
        private final Map<FloatingMenuInteraction, FloatingMenuAction> triggers =
                new EnumMap<>(FloatingMenuInteraction.class);

        private Builder(Component title, boolean titleVisible) {
            this.title = Objects.requireNonNull(title, "title");
            this.titleVisible = titleVisible;
        }

        /** Declares an item-display node with an explicit spatial label. */
        public NodeBuilder item(String id, ItemStack item, Component label) {
            requireNewId(id);
            return new NodeBuilder(this, id, Objects.requireNonNull(item, "item"),
                    Objects.requireNonNull(label, "label"), FloatingMenuNodeRole.ITEM);
        }

        public NodeBuilder item(String id, Material material, Component label) {
            return item(id, new ItemStack(Objects.requireNonNull(material, "material")), label);
        }

        /** Adds a non-interactive node that does not participate in layout or focus state. */
        public Builder decoration(FloatingMenuDecoration decoration) {
            Objects.requireNonNull(decoration, "decoration");
            requireNewId(decoration.id());
            decorations.put(decoration.id(), decoration);
            return this;
        }

        public Builder textDecoration(String id, FloatingMenuPoint point, Component text) {
            return decoration(FloatingMenuDecoration.text(id, point, text));
        }

        public Builder textDecoration(String id, FloatingMenuPose pose, Component text) {
            return decoration(FloatingMenuDecoration.text(id, pose, text));
        }

        public Builder textDecoration(String id, FloatingMenuPoint point, Component text,
                                      int background, float displayWidth, float displayHeight) {
            return decoration(FloatingMenuDecoration.text(id, point, text,
                    background, displayWidth, displayHeight));
        }

        public Builder textDecoration(String id, FloatingMenuPoint point, Component text,
                                      int background, float displayWidth, float displayHeight,
                                      float scale) {
            return decoration(FloatingMenuDecoration.text(id, point, text,
                    background, displayWidth, displayHeight, scale));
        }

        public Builder textDecoration(String id, FloatingMenuPoint point, Component text,
                                      int background, float displayWidth, float displayHeight,
                                      float scale, FloatingMenuDecoration.Alignment alignment) {
            return decoration(FloatingMenuDecoration.text(id, point, text,
                    background, displayWidth, displayHeight, scale, alignment));
        }

        public Builder textDecoration(String id, FloatingMenuPose pose, Component text,
                                      int background, float displayWidth, float displayHeight,
                                      float scale, FloatingMenuDecoration.Alignment alignment) {
            return decoration(FloatingMenuDecoration.text(id, pose, text,
                    background, displayWidth, displayHeight, scale, alignment));
        }

        public Builder itemDecoration(String id, FloatingMenuPoint point, ItemStack item) {
            return itemDecoration(id, point, item, 0.65F,
                    FloatingMenuDecoration.Motion.BOB);
        }

        public Builder itemDecoration(String id, FloatingMenuPose pose, ItemStack item) {
            return itemDecoration(id, pose, item, 0.65F,
                    FloatingMenuDecoration.Motion.BOB);
        }

        public Builder itemDecoration(String id, FloatingMenuPoint point, ItemStack item,
                                      float scale, FloatingMenuDecoration.Motion motion) {
            return decoration(FloatingMenuDecoration.item(id, point, item, scale, motion));
        }

        public Builder itemDecoration(String id, FloatingMenuPose pose, ItemStack item,
                                      float scale, FloatingMenuDecoration.Motion motion) {
            return decoration(FloatingMenuDecoration.item(id, pose, item, scale, motion));
        }

        /** Places a client-only item at an absolute world coordinate. */
        public Builder worldItemDecoration(String id, Location location,
                                           double yawDegrees, double pitchDegrees,
                                           ItemStack item, float scale,
                                           FloatingMenuDecoration.Motion motion) {
            return decoration(FloatingMenuDecoration.worldItem(id, location,
                    yawDegrees, pitchDegrees, item, scale, motion));
        }

        /** Places client-only text at an absolute world coordinate. */
        public Builder worldTextDecoration(String id, Location location,
                                           double yawDegrees, double pitchDegrees,
                                           Component text, int background,
                                           float displayWidth, float displayHeight,
                                           float scale,
                                           FloatingMenuDecoration.Alignment alignment) {
            return decoration(FloatingMenuDecoration.worldText(id, location,
                    yawDegrees, pitchDegrees, text, background,
                    displayWidth, displayHeight, scale, alignment));
        }

        public Builder blockDecoration(String id, FloatingMenuPoint point, ItemStack item,
                                       float scale, FloatingMenuDecoration.Motion motion) {
            return decoration(FloatingMenuDecoration.block(id, point, item, scale, motion));
        }

        public Builder blockDecoration(String id, FloatingMenuPose pose, ItemStack item,
                                       float scale, FloatingMenuDecoration.Motion motion) {
            return decoration(FloatingMenuDecoration.block(id, pose, item, scale, motion));
        }

        /** Places a client-only block at an absolute world coordinate. */
        public Builder worldBlockDecoration(String id, Location location,
                                            double yawDegrees, double pitchDegrees,
                                            ItemStack item, float scale,
                                            FloatingMenuDecoration.Motion motion) {
            return decoration(FloatingMenuDecoration.worldBlock(id, location,
                    yawDegrees, pitchDegrees, item, scale, motion));
        }

        public Builder blockDecoration(String id, FloatingMenuPoint point, Material material,
                                       float scale, FloatingMenuDecoration.Motion motion) {
            if (material == null || !material.isBlock()) {
                throw new IllegalArgumentException("Block decoration requires a block material");
            }
            return blockDecoration(id, point, new ItemStack(material), scale, motion);
        }

        public Builder blockDecoration(String id, FloatingMenuPose pose, Material material,
                                       float scale, FloatingMenuDecoration.Motion motion) {
            if (material == null || !material.isBlock()) {
                throw new IllegalArgumentException("Block decoration requires a block material");
            }
            return blockDecoration(id, pose, new ItemStack(material), scale, motion);
        }

        /** Declares passive text that participates in spatial layout but never owns input. */
        public NodeBuilder information(String id, Component text) {
            requireNewId(id);
            return new NodeBuilder(this, id, Objects.requireNonNull(text, "text"),
                    FloatingMenuNodeRole.INFORMATION);
        }

        /** Declares a text control with no item-metadata dependency. */
        public NodeBuilder control(String id, Component label) {
            requireNewId(id);
            return new NodeBuilder(this, id, Objects.requireNonNull(label, "label"),
                    FloatingMenuNodeRole.CONTROL);
        }

        /** Declares a semantic spatial navigation control such as Back, Close, or Next. */
        public NodeBuilder navigation(String id, Component label) {
            requireNewId(id);
            return new NodeBuilder(this, id, Objects.requireNonNull(label, "label"),
                    FloatingMenuNodeRole.NAVIGATION);
        }

        /** Adds the conventional Back control and its hierarchy action. */
        public NodeBuilder back(Component label) {
            return navigation("back", label).primary((player, menu) -> menu.back());
        }

        /** Adds the conventional Close control and its terminal action. */
        public NodeBuilder close(Component label) {
            return navigation("close", label).primary((player, menu) -> menu.close());
        }

        /** Goes back when this is a child screen, otherwise closes the root screen. */
        public NodeBuilder dismiss(Component label) {
            return navigation("dismiss", label).primary((player, menu) -> {
                if (menu.depth() > 0) menu.back();
                else menu.close();
            });
        }

        /**
         * Adds a two-state item control with consistent selected-state semantics.
         * The caller can append its region and action through the returned node builder.
         */
        public NodeBuilder toggle(String id, boolean selected,
                                  Material selectedMaterial, Material unselectedMaterial,
                                  Component label) {
            Objects.requireNonNull(selectedMaterial, "selectedMaterial");
            Objects.requireNonNull(unselectedMaterial, "unselectedMaterial");
            return item(id, selected ? selectedMaterial : unselectedMaterial,
                    label).selected(selected);
        }

        /** Adds a single-material choice with consistent selected-state semantics. */
        public NodeBuilder choice(String id, boolean selected,
                                  Material material, Component label) {
            return item(id, material, label).selected(selected);
        }

        /**
         * Adds conventional previous/count/next controls and matching scroll actions.
         * Missing directions are omitted, so compact pagers remain centered.
         */
        public Builder pagination(String region, FloatingMenuPage page,
                                  Component previousLabel, Component pageLabel,
                                  Component nextLabel, IntConsumer selectPage) {
            Objects.requireNonNull(page, "page");
            Objects.requireNonNull(selectPage, "selectPage");
            if (region == null || region.isBlank()) {
                throw new IllegalArgumentException("Pagination region must not be blank");
            }
            if (page.hasPrevious()) {
                int previous = page.previous().index();
                navigation("page:previous", Objects.requireNonNull(previousLabel, "previousLabel"))
                        .region(region)
                        .primary((player, menu) -> selectPage.accept(previous));
                on(FloatingMenuInteraction.SCROLL_UP,
                        (player, menu, input) -> selectPage.accept(previous));
            }
            information("page", Objects.requireNonNull(pageLabel, "pageLabel")).region(region);
            if (page.hasNext()) {
                int next = page.next().index();
                navigation("page:next", Objects.requireNonNull(nextLabel, "nextLabel"))
                        .region(region)
                        .primary((player, menu) -> selectPage.accept(next));
                on(FloatingMenuInteraction.SCROLL_DOWN,
                        (player, menu, input) -> selectPage.accept(next));
            }
            return this;
        }

        /** Adds the shared compact pager used by spatial browsers and lists. */
        public Builder pagination(String region, FloatingMenuPage page,
                                  IntConsumer selectPage) {
            Objects.requireNonNull(page, "page");
            return pagination(region, page,
                    Component.text("‹", NamedTextColor.YELLOW),
                    Component.text((page.index() + 1) + " / " + page.count(),
                            NamedTextColor.GRAY),
                    Component.text("›", NamedTextColor.YELLOW), selectPage);
        }

        /** Declares a block-display node with explicit presentation text. */
        public NodeBuilder block(String id, Material material, Component label) {
            if (material == null || !material.isBlock()) {
                throw new IllegalArgumentException("Block node requires a block material");
            }
            requireNewId(id);
            return new NodeBuilder(this, id, new ItemStack(material),
                    Objects.requireNonNull(label, "label"), FloatingMenuNodeRole.BLOCK);
        }

        public Builder animation(FloatingMenuAnimation animation) {
            this.animation = Objects.requireNonNull(animation, "animation");
            return this;
        }

        public Builder layout(FloatingMenuLayout layout) {
            this.layout = Objects.requireNonNull(layout, "layout");
            return this;
        }

        public Builder feedback(FloatingMenuFeedback feedback) {
            this.feedback = Objects.requireNonNull(feedback, "feedback");
            return this;
        }

        public Builder appearance(FloatingMenuAppearance appearance) {
            this.appearance = Objects.requireNonNull(appearance, "appearance");
            return this;
        }

        /** Selects the angular envelope used when fitting the complete scene. */
        public Builder framing(FloatingMenuFraming framing) {
            this.framing = Objects.requireNonNull(framing, "framing");
            return this;
        }

        /** Requires the spatial projection even when a client-native form is available. */
        public Builder requireSpatialPresentation() {
            this.presentation = FloatingMenuPresentation.SPATIAL_REQUIRED;
            return this;
        }

        public Builder presentation(FloatingMenuPresentation presentation) {
            this.presentation = Objects.requireNonNull(presentation, "presentation");
            return this;
        }

        /** Keeps the opening anchor stable for frequently updated animated scenes. */
        public Builder stableAnchor() {
            this.anchorMode = FloatingMenuAnchorMode.FIXED_FOR_SESSION;
            return this;
        }

        public Builder anchorMode(FloatingMenuAnchorMode anchorMode) {
            this.anchorMode = Objects.requireNonNull(anchorMode, "anchorMode");
            return this;
        }

        /** Selects whether this scene follows the player's pose or uses standing eye level. */
        public Builder viewpoint(FloatingMenuViewpoint viewpoint) {
            this.viewpoint = Objects.requireNonNull(viewpoint, "viewpoint");
            return this;
        }

        /** Selects the safety leash appropriate to this screen's input model. */
        public Builder movementPolicy(FloatingMenuMovementPolicy movementPolicy) {
            this.movementPolicy = Objects.requireNonNull(movementPolicy, "movementPolicy");
            return this;
        }

        /** Adds or replaces the optional title node for this scene. */
        public Builder title(Component title) {
            this.title = Objects.requireNonNull(title, "title");
            this.titleVisible = true;
            return this;
        }

        /** Removes the title node entirely rather than rendering empty text. */
        public Builder withoutTitle() {
            this.title = Component.empty();
            this.titleVisible = false;
            return this;
        }

        public Builder lifecycle(FloatingMenuLifecycle lifecycle) {
            this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
            return this;
        }

        /** Rebuilds live content at a fixed cadence while this screen remains in the hierarchy. */
        public Builder refreshEvery(int intervalTicks, FloatingMenuCommand action) {
            this.refresh = FloatingMenuRefresh.every(intervalTicks, action);
            return this;
        }

        /** Rebuilds only when the sampled revision differs from the last rendered revision. */
        public Builder refreshWhenChanged(int intervalTicks,
                                          Function<Player, ?> revision,
                                          FloatingMenuCommand action) {
            this.refresh = FloatingMenuRefresh.whenChanged(intervalTicks, revision, action);
            return this;
        }

        /** Receives decoration packet commit timestamps for time-critical scenes. */
        public Builder observeFrames(FloatingMenuFrameObserver observer) {
            this.frameObserver = Objects.requireNonNull(observer, "observer");
            return this;
        }

        /** Stable logical identity used for reconciliation and hierarchy pop-to behavior. */
        public Builder screen(String screenId) {
            if (screenId == null || screenId.isBlank()) {
                throw new IllegalArgumentException("Screen id must not be blank");
            }
            this.screenId = screenId;
            return this;
        }

        public Builder on(FloatingMenuInteraction interaction, FloatingMenuAction action) {
            triggers.put(Objects.requireNonNull(interaction, "interaction"),
                    Objects.requireNonNull(action, "action"));
            return this;
        }

        public Builder onScroll(FloatingMenuAction up, FloatingMenuAction down) {
            return on(FloatingMenuInteraction.SCROLL_UP, up)
                    .on(FloatingMenuInteraction.SCROLL_DOWN, down);
        }

        public Builder onScroll(FloatingMenuCommand up, FloatingMenuCommand down) {
            Objects.requireNonNull(up, "up");
            Objects.requireNonNull(down, "down");
            return onScroll((player, menu, interaction) -> up.execute(player, menu),
                    (player, menu, interaction) -> down.execute(player, menu));
        }

        public FloatingMenuDefinition build() {
            return new FloatingMenuDefinition(title, titleVisible, screenId, entries, decorations,
                    animation, layout, feedback, appearance, framing,
                    presentation, anchorMode, viewpoint, movementPolicy,
                    triggers, lifecycle, refresh, frameObserver);
        }

        private void requireNewId(String id) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Element id must not be blank");
            if (entries.containsKey(id) || decorations.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate scene node id: " + id);
            }
        }
    }

    public static final class NodeBuilder {
        private final Builder parent;
        private final String id;
        private final ItemStack item;
        private String region = DEFAULT_REGION;
        private final Component label;
        private final Map<FloatingMenuInteraction, FloatingMenuAction> triggers =
                new EnumMap<>(FloatingMenuInteraction.class);
        private final FloatingMenuNodeRole role;
        private FloatingMenuTextWidth textWidth = FloatingMenuTextWidth.AUTO;
        private FloatingMenuFocusAction focusAction;
        private boolean interactive;
        private FloatingMenuDecoration.Alignment alignment =
                FloatingMenuDecoration.Alignment.CENTER;
        private boolean selected;
        private boolean enabled = true;
        private Component disabledReason;

        private NodeBuilder(Builder parent, String id, ItemStack item,
                            Component label, FloatingMenuNodeRole role) {
            this.parent = parent;
            this.id = id;
            this.item = item.clone();
            this.label = label;
            this.role = role;
            this.interactive = role.interactiveByDefault();
            commit();
        }

        private NodeBuilder(Builder parent, String id, Component label,
                            FloatingMenuNodeRole role) {
            this.parent = parent;
            this.id = id;
            this.item = null;
            this.label = label;
            this.role = role;
            this.interactive = role.interactiveByDefault();
            commit();
        }

        public NodeBuilder region(String region) {
            if (region == null || region.isBlank()) throw new IllegalArgumentException("Region must not be blank");
            this.region = region;
            return commit();
        }

        public NodeBuilder on(FloatingMenuInteraction interaction, FloatingMenuAction action) {
            requireActions();
            interactive = true;
            triggers.put(Objects.requireNonNull(interaction, "interaction"),
                    Objects.requireNonNull(action, "action"));
            return commit();
        }

        public NodeBuilder primary(FloatingMenuAction action) { return on(FloatingMenuInteraction.PRIMARY, action); }
        public NodeBuilder secondary(FloatingMenuAction action) { return on(FloatingMenuInteraction.SECONDARY, action); }
        public NodeBuilder hotkey(FloatingMenuAction action) { return on(FloatingMenuInteraction.HOTKEY, action); }
        public NodeBuilder scrollUp(FloatingMenuAction action) { return on(FloatingMenuInteraction.SCROLL_UP, action); }
        public NodeBuilder scrollDown(FloatingMenuAction action) { return on(FloatingMenuInteraction.SCROLL_DOWN, action); }

        public NodeBuilder primary(FloatingMenuCommand action) { return command(FloatingMenuInteraction.PRIMARY, action); }
        public NodeBuilder secondary(FloatingMenuCommand action) { return command(FloatingMenuInteraction.SECONDARY, action); }
        public NodeBuilder hotkey(FloatingMenuCommand action) { return command(FloatingMenuInteraction.HOTKEY, action); }
        public NodeBuilder scrollUp(FloatingMenuCommand action) { return command(FloatingMenuInteraction.SCROLL_UP, action); }
        public NodeBuilder scrollDown(FloatingMenuCommand action) { return command(FloatingMenuInteraction.SCROLL_DOWN, action); }

        private NodeBuilder command(FloatingMenuInteraction interaction, FloatingMenuCommand action) {
            Objects.requireNonNull(action, "action");
            return on(interaction, (player, menu, input) -> action.execute(player, menu));
        }

        public NodeBuilder focus(FloatingMenuFocusAction action) {
            requireActions();
            interactive = true;
            focusAction = Objects.requireNonNull(action, "action");
            return commit();
        }

        /**
         * Marks this node as the persistent choice in a radio/selection group.
         * This is visual state, not a synthetic click or hover state.
         */
        public NodeBuilder selected(boolean selected) {
            requireActions();
            this.selected = selected;
            return commit();
        }

        /** Aligns multiline presentation text without changing node geometry. */
        public NodeBuilder alignment(FloatingMenuDecoration.Alignment alignment) {
            this.alignment = Objects.requireNonNull(alignment, "alignment");
            return commit();
        }

        /** Selects a semantic wrapping width; measured layout and hit geometry follow it. */
        public NodeBuilder textWidth(FloatingMenuTextWidth textWidth) {
            this.textWidth = Objects.requireNonNull(textWidth, "textWidth");
            return commit();
        }

        /** Keeps an item or block as scene content without hover, hitbox, or input state. */
        public NodeBuilder passive() {
            if (!role.supportsPassivePresentation()) {
                throw new IllegalStateException("Only item and block nodes can become passive");
            }
            interactive = false;
            triggers.clear();
            focusAction = null;
            return commit();
        }

        public NodeBuilder disabled(Component reason) {
            requireActions();
            enabled = false;
            disabledReason = Objects.requireNonNull(reason, "reason");
            return commit();
        }

        private NodeBuilder commit() {
            parent.entries.put(id, new Entry(id, region, item, label, role, textWidth, triggers,
                    focusAction, interactive, alignment,
                    selected, enabled, disabledReason));
            return this;
        }

        private void requireActions() {
            if (!role.supportsActions()) {
                throw new IllegalStateException("Information nodes cannot own actions or state");
            }
        }
    }

}
