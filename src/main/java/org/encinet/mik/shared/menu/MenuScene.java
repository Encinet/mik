package org.encinet.mik.shared.menu;

import org.encinet.mik.shared.menu.ui.UiTemplates;
import org.encinet.mik.shared.menu.ui.UiSize;
import org.encinet.mik.shared.menu.ui.UiNode;
import org.encinet.mik.shared.menu.ui.UiKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.encinet.mik.shared.menu.ui.UiRole;
import org.encinet.mik.shared.menu.ui.UiTooltip;
import org.encinet.mik.shared.menu.ui.UiFrame;
import org.encinet.mik.shared.menu.ui.UiContent;
import org.encinet.mik.shared.menu.ui.UiPointerBehavior;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntConsumer;

/** Immutable, semantic scene description consumed by the floating-menu renderer. */
public final class MenuScene {
    public static final String DEFAULT_REGION = "default";
    public static final UiSize DEFAULT_VIEWPORT = new UiSize(1200, 600);

    private final UiNode componentTree;
    private final UiSize viewport;
    private final Component title;
    private final boolean titleVisible;
    private final String screenId;
    private final boolean loading;
    private final MenuPopover popover;
    private final Map<UiKey,UiKey> sharedElements;
    private final Map<String, Entry> entries;
    private final Map<String, FloatingMenuDecoration> decorations;
    private final Set<String> framingDecorations;
    private final FloatingMenuAnimation animation;
    private final FloatingMenuFeedback feedback;
    private final FloatingMenuAppearance appearance;
    private final FloatingMenuFraming framing;
    private final FloatingMenuAnchorMode anchorMode;
    private final FloatingMenuSpatialFrame spatialFrame;
    private final FloatingMenuViewpoint viewpoint;
    private final FloatingMenuMovementPolicy movementPolicy;
    private final Map<FloatingMenuInteraction, FloatingMenuAction> triggers;
    private final FloatingMenuLifecycle lifecycle;
    private final FloatingMenuRefresh refresh;
    private final FloatingMenuFrameObserver frameObserver;

    private MenuScene(Component title, boolean titleVisible,
                                   String screenId, Map<String, Entry> entries,
                                   Map<String, FloatingMenuDecoration> decorations,
                                   Set<String> framingDecorations,
                                   FloatingMenuAnimation animation,
                                   FloatingMenuFeedback feedback, FloatingMenuAppearance appearance,
                                   FloatingMenuFraming framing,
                                   FloatingMenuAnchorMode anchorMode,
                                   FloatingMenuSpatialFrame spatialFrame,
                                   FloatingMenuViewpoint viewpoint,
                                   FloatingMenuMovementPolicy movementPolicy,
                                   Map<FloatingMenuInteraction, FloatingMenuAction> triggers,
                                   FloatingMenuLifecycle lifecycle,
                                   FloatingMenuRefresh refresh,
                                   FloatingMenuFrameObserver frameObserver,
                                   UiNode componentTree,
                                   UiSize viewport, boolean loading, MenuPopover popover, Map<UiKey,UiKey> sharedElements) {
        this.componentTree = componentTree;
        this.viewport = viewport;
        this.title = title;
        this.titleVisible = titleVisible;
        this.screenId = screenId;
        this.loading = loading;
        this.popover = popover;
        this.sharedElements = Map.copyOf(sharedElements);
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        this.decorations = Collections.unmodifiableMap(new LinkedHashMap<>(decorations));
        this.framingDecorations = Set.copyOf(framingDecorations);
        this.animation = animation;
        this.feedback = feedback;
        this.appearance = appearance;
        this.framing = framing;
        this.anchorMode = anchorMode;
        this.spatialFrame = spatialFrame;
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
        return builder().screen(screenId).appearance(FloatingMenuAppearance.DEFAULT).stableAnchor();
    }

    /** Creates a titled application screen with the shared spatial treatment. */
    public static Builder screen(String screenId, Component title) {
        return builder(title).screen(screenId).appearance(FloatingMenuAppearance.DEFAULT).stableAnchor();
    }

    public UiNode content() { return componentTree; }
    public UiSize viewport() { return viewport; }

    public Component title() { return title; }
    public boolean titleVisible() { return titleVisible; }
    public String screenId() { return screenId; }
    public boolean loading() { return loading; }
    public MenuPopover popover() { return popover; }
    public Map<UiKey,UiKey> sharedElements() { return sharedElements; }
    public Map<String, Entry> entries() { return entries; }
    public Map<String, FloatingMenuDecoration> decorations() { return decorations; }
    public Set<String> framingDecorations() { return framingDecorations; }
    public FloatingMenuAnimation animation() { return animation; }
    public FloatingMenuFeedback feedback() { return feedback; }
    public FloatingMenuAppearance appearance() { return appearance; }
    public FloatingMenuFraming framing() { return framing; }
    public FloatingMenuAnchorMode anchorMode() { return anchorMode; }
    public FloatingMenuSpatialFrame spatialFrame() { return spatialFrame; }
    public FloatingMenuViewpoint viewpoint() { return viewpoint; }
    public FloatingMenuMovementPolicy movementPolicy() { return movementPolicy; }
    public Map<FloatingMenuInteraction, FloatingMenuAction> triggers() { return triggers; }
    public FloatingMenuLifecycle lifecycle() { return lifecycle; }
    public FloatingMenuRefresh refresh() { return refresh; }
    public FloatingMenuFrameObserver frameObserver() { return frameObserver; }

    /** Copies the resolved component tree and policies so a host can frame feature-owned content. */
    public Builder toBuilder() {
        Builder copy = new Builder(title, titleVisible);
        copy.componentTree = componentTree;
        copy.viewport = viewport;
        copy.screenId = screenId;
        copy.loading = loading;
        copy.popover = popover;
        copy.sharedElements.putAll(sharedElements);
        copy.decorations.putAll(decorations);
        copy.framingDecorations = framingDecorations;
        copy.animation = animation;
        copy.feedback = feedback;
        copy.appearance = appearance;
        copy.framing = framing;
        copy.anchorMode = anchorMode;
        copy.spatialFrame = spatialFrame;
        copy.viewpoint = viewpoint;
        copy.movementPolicy = movementPolicy;
        copy.triggers.putAll(triggers);
        copy.lifecycle = lifecycle;
        copy.refresh = refresh;
        copy.frameObserver = frameObserver;
        return copy;
    }

    MenuScene identifiedBy(String id) {
        if (screenId != null && !screenId.equals(id)) {
            throw new IllegalArgumentException("Renderer screen id '" + screenId
                    + "' does not match owner '" + id + "'");
        }
        if (id.equals(screenId)) return this;
        return new MenuScene(title, titleVisible, id, entries, decorations, framingDecorations,
                animation, feedback, appearance, framing,
                anchorMode, spatialFrame, viewpoint, movementPolicy,
                triggers, lifecycle, refresh, frameObserver, componentTree, viewport, loading, popover, sharedElements);
    }

    MenuScene withLifecycle(FloatingMenuLifecycle nextLifecycle) {
        return new MenuScene(title, titleVisible, screenId, entries, decorations, framingDecorations,
                animation, feedback, appearance, framing,
                anchorMode, spatialFrame, viewpoint, movementPolicy,
                triggers, FloatingMenuLifecycle.combine(lifecycle,
                        Objects.requireNonNull(nextLifecycle, "nextLifecycle")), refresh,
                frameObserver, componentTree, viewport, loading, popover, sharedElements);
    }

    /** Rendering, hit targets and wheel actions use the same realized page of a measured collection. */
    public MenuScene measured(UiFrame frame) { return measured(frame, null); }

    /** Each layer realizes its own collection while keeping the parent's navigation separate. */
    public MenuScene measured(UiFrame frame, UiFrame companion) {
        Map<String, Entry> visible = new LinkedHashMap<>();
        for (var element : frame.elements()) {
            if (!(element.node().content() instanceof UiContent.Image))
                visible.put(element.path(), UiSceneContent.entry(element.path(), element.node()));
        }
        if (companion != null) for (var element : companion.elements()) {
            if (!(element.node().content() instanceof UiContent.Image)) {
                String path = MenuPopover.scoped(element.path());
                visible.put(path, UiSceneContent.entry(path, element.node()));
            }
        }
        var actions = new EnumMap<FloatingMenuInteraction, FloatingMenuAction>(FloatingMenuInteraction.class);
        actions.putAll(triggers);
        if (!frame.pagination().isEmpty()) {
            actions.remove(FloatingMenuInteraction.SCROLL_UP);
            actions.remove(FloatingMenuInteraction.SCROLL_DOWN);
            if (frame.pagination().size() == 1) {
                var pagination = frame.pagination().getFirst();
                actions.put(FloatingMenuInteraction.SCROLL_UP, pagination.action(FloatingMenuInteraction.SCROLL_UP));
                actions.put(FloatingMenuInteraction.SCROLL_DOWN, pagination.action(FloatingMenuInteraction.SCROLL_DOWN));
            }
        }
        return new MenuScene(title, titleVisible, screenId, visible, decorations, framingDecorations,
                animation, feedback, appearance, framing, anchorMode, spatialFrame, viewpoint, movementPolicy,
                actions, lifecycle, refresh, frameObserver, componentTree, viewport, loading, companion == null ? null : popover, sharedElements);
    }

    /** One independently addressable node in the floating scene. */
    public record Entry(String id, String region, ItemStack item,
                        Component label, UiRole role, FloatingMenuElementStyle style,
                        FloatingMenuTextWidth textWidth,
                        Map<FloatingMenuInteraction, FloatingMenuAction> triggers,
                        FloatingMenuFocusAction focusAction, boolean interactive,
                        FloatingMenuDecoration.Alignment alignment,
                        boolean selected, boolean enabled, boolean keepAccessible,
                        Component disabledReason, UiTooltip tooltip) {
        public Entry {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Element id must not be blank");
            if (region == null || region.isBlank()) throw new IllegalArgumentException("Region must not be blank");
            label = Objects.requireNonNull(label, "label");
            role = Objects.requireNonNull(role, "role");
            style = Objects.requireNonNull(style, "style");
            textWidth = Objects.requireNonNull(textWidth, "textWidth");
            alignment = Objects.requireNonNull(alignment, "alignment");
            triggers = Map.copyOf(Objects.requireNonNull(triggers, "triggers"));
            item = switch (style) {
                case TEXT -> {
                    if (item != null) {
                        throw new IllegalArgumentException("Text nodes cannot own item visuals");
                    }
                    yield null;
                }
                case ITEM, BLOCK -> Objects.requireNonNull(item,
                        "Visual elements require an item").clone();
            };
            if (style == FloatingMenuElementStyle.BLOCK && !item.getType().isBlock()) {
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

        public UiPointerBehavior pointerBehavior() {
            return UiPointerBehavior.resolve(enabled, !triggers.isEmpty(),
                    focusAction != null || disabledReason != null || tooltip != null);
        }

        public FloatingMenuAction trigger(FloatingMenuInteraction interaction) {
            return triggers.get(interaction);
        }
    }

    public static final class Builder {
        private UiNode componentTree = UiTemplates.actions(4);
        private UiSize viewport = DEFAULT_VIEWPORT;
        private Component title;
        private boolean titleVisible;
        private String screenId;
        private boolean loading;
        private MenuPopover popover;
        private final Map<UiKey,UiKey> sharedElements = new LinkedHashMap<>();
        private final Map<String, Entry> entries = new LinkedHashMap<>();
        private final Map<String, FloatingMenuDecoration> decorations = new LinkedHashMap<>();
        private Set<String> framingDecorations = Set.of();
        private FloatingMenuAnimation animation = FloatingMenuAnimation.DEFAULT;
        private FloatingMenuFeedback feedback = FloatingMenuFeedback.DEFAULT;
        private FloatingMenuAppearance appearance = FloatingMenuAppearance.DEFAULT;
        private FloatingMenuFraming framing = FloatingMenuFraming.COMFORTABLE;
        private FloatingMenuAnchorMode anchorMode = FloatingMenuAnchorMode.ADAPTIVE;
        private FloatingMenuSpatialFrame spatialFrame = FloatingMenuSpatialFrame.IN_FRONT;
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
                    Objects.requireNonNull(label, "label"), UiRole.ACTION, FloatingMenuElementStyle.ITEM);
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
            return new NodeBuilder(this, id, null, Objects.requireNonNull(text, "text"), UiRole.TEXT, FloatingMenuElementStyle.TEXT);
        }

        /** Declares a text control with no item-metadata dependency. */
        public NodeBuilder control(String id, Component label) {
            requireNewId(id);
            return new NodeBuilder(this, id, null, Objects.requireNonNull(label, "label"), UiRole.ACTION, FloatingMenuElementStyle.TEXT);
        }

        /** Declares a semantic spatial navigation control such as Back, Close, or Next. */
        public NodeBuilder navigation(String id, Component label) {
            requireNewId(id);
            return new NodeBuilder(this, id, null, Objects.requireNonNull(label, "label"), UiRole.NAVIGATION, FloatingMenuElementStyle.TEXT);
        }

        /** Adds the conventional Back control and its hierarchy action. */
        public NodeBuilder back(Component label) {
            return navigation("back", label).primary((_, menu) -> menu.back());
        }

        /** Adds the conventional Close control and its terminal action. */
        public NodeBuilder close(Component label) {
            return navigation("close", label).primary((_, menu) -> menu.close());
        }

        /** Goes back when this is a child screen, otherwise closes the root screen. */
        public NodeBuilder dismiss(Component label) {
            return navigation("dismiss", label).primary((_, menu) -> {
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
         * Missing buttons are omitted. Both wheel directions remain captured at the boundaries.
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
                        .primary((_, _) -> selectPage.accept(previous));
            }
            information("page", Objects.requireNonNull(pageLabel, "pageLabel")).region(region);
            if (page.hasNext()) {
                int next = page.next().index();
                navigation("page:next", Objects.requireNonNull(nextLabel, "nextLabel"))
                        .region(region)
                        .primary((_, _) -> selectPage.accept(next));
            }
            onScroll((_, _) -> {
                if (page.hasPrevious()) selectPage.accept(page.previous().index());
            }, (_, _) -> {
                if (page.hasNext()) selectPage.accept(page.next().index());
            });
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
                    Objects.requireNonNull(label, "label"), UiRole.ACTION, FloatingMenuElementStyle.BLOCK);
        }

        public Builder popover(MenuPopover popover) { this.popover = popover; return this; }

        public Builder content(UiNode content) {
            this.componentTree = Objects.requireNonNull(content, "content");
            return this;
        }

        /** Maximum logical space available to responsive layouts before world projection. */
        public Builder viewport(UiSize size) {
            if (size.width() <= 0 || size.height() <= 0 || size.width() > 4096 || size.height() > 4096)
                throw new IllegalArgumentException("Scene viewport must be positive and bounded");
            viewport = size;
            return this;
        }

        public Builder animation(FloatingMenuAnimation animation) {
            this.animation = Objects.requireNonNull(animation, "animation");
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

        /** Keeps the scene anchored where this session opened. */
        public Builder stableAnchor() {
            this.anchorMode = FloatingMenuAnchorMode.FIXED_FOR_SESSION;
            return this;
        }

        public Builder anchorMode(FloatingMenuAnchorMode anchorMode) {
            this.anchorMode = Objects.requireNonNull(anchorMode, "anchorMode");
            return this;
        }

        public Builder taskToolbar() {
            return anchorMode(FloatingMenuAnchorMode.INERTIAL_TOOLBAR)
                    .movementPolicy(FloatingMenuMovementPolicy.CAPTURED_INPUT).frontArc();
        }

        /** Anchors local positions around the viewer instead of fitting them into a front panel. */
        public Builder aroundViewer() {
            this.spatialFrame = FloatingMenuSpatialFrame.AROUND_VIEWER;
            return this;
        }

        public Builder frontArc() {
            this.spatialFrame = FloatingMenuSpatialFrame.FRONT_ARC;
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

        /** A temporary placeholder: retain an existing page, or wait for content to establish its reading frame. */
        public Builder loading(boolean loading) {
            this.loading = loading;
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
            return onScroll((player, menu, _) -> up.execute(player, menu),
                    (player, menu, _) -> down.execute(player, menu));
        }

        public Builder framingDecorations(String... ids) {
            framingDecorations = Set.of(ids);
            return this;
        }

        /** Explicit domain identity and local node identity; neither labels nor list positions establish a match. */
        public Builder sharedElement(UiKey identity, UiKey node) {
            Objects.requireNonNull(identity, "identity"); Objects.requireNonNull(node, "node");
            if (identity.equals(UiKey.AUTO) || node.equals(UiKey.AUTO))
                throw new IllegalArgumentException("Shared elements need explicit identities");
            if (sharedElements.putIfAbsent(identity,node) != null)
                throw new IllegalArgumentException("Duplicate shared object: " + identity);
            return this;
        }

        public MenuScene build() {
            UiNode resolvedContent = UiSceneContent.resolve(componentTree, entries);
            for (String id : framingDecorations) {
                if (!decorations.containsKey(id) || decorations.get(id).worldAnchored())
                    throw new IllegalArgumentException("Framing decoration must be a local scene node: " + id);
            }
            var resolvedEntries = new LinkedHashMap<>(UiSceneContent.entries(resolvedContent));
            if (popover != null) UiSceneContent.elements(popover.contentTree()).forEach((path, element) -> {
                if (!(element.content() instanceof UiContent.Image))
                    resolvedEntries.put(MenuPopover.scoped(path), UiSceneContent.entry(MenuPopover.scoped(path), element));
            });
            return new MenuScene(title, titleVisible, screenId,
                    resolvedEntries, decorations, framingDecorations,
                    animation, feedback, appearance, framing,
                    anchorMode, spatialFrame, viewpoint, movementPolicy,
                    triggers, lifecycle, refresh, frameObserver, resolvedContent, viewport, loading, popover, sharedElements);
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
        private UiRole role;
        private final FloatingMenuElementStyle style;
        private FloatingMenuTextWidth textWidth = FloatingMenuTextWidth.AUTO;
        private FloatingMenuFocusAction focusAction;
        private boolean interactive;
        private FloatingMenuDecoration.Alignment alignment =
                FloatingMenuDecoration.Alignment.CENTER;
        private boolean selected;
        private boolean enabled = true;
        private boolean keepAccessible;
        private Component disabledReason;
        private UiTooltip tooltip;

        private NodeBuilder(Builder parent, String id, ItemStack item,
                            Component label, UiRole role, FloatingMenuElementStyle style) {
            this.parent = parent;
            this.id = id;
            this.item = item == null ? null : item.clone();
            this.label = label;
            this.role = role;
            this.style = style;
            this.interactive = role.supportsActions();
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
            return on(interaction, (player, menu, _) -> action.execute(player, menu));
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
            this.role = UiRole.CHOICE;
            return commit();
        }

        public NodeBuilder tooltip(Component text) {
            tooltip = UiTooltip.of(text);
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

        /** Keeps an essential control readable in a cramped player-centered scene. */
        public NodeBuilder keepAccessible() {
            this.keepAccessible = true;
            return commit();
        }

        /** Keeps an item or block as read-only scene content; optional help remains inspectable. */
        public NodeBuilder passive() {
            if (style == FloatingMenuElementStyle.TEXT) {
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
            parent.entries.put(id, new Entry(id, region, item, label, role, style, textWidth, triggers,
                    focusAction, interactive, alignment,
                    selected, enabled, keepAccessible, disabledReason, tooltip));
            return this;
        }

        private void requireActions() {
            if (!role.supportsActions()) {
                throw new IllegalStateException("Information nodes cannot own actions or state");
            }
        }
    }

}
