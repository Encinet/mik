package org.encinet.mik.feature.world.vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.FloatingMenuService;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.joml.Matrix4f;
import org.mockito.MockedStatic;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real editor actions and scene rendering, with observable private-display and menu platform boundaries. */
final class VehicleEditorHarness implements AutoCloseable {
    final World world = mock(World.class);
    final Player player = mock(Player.class);
    final JavaPlugin plugin = mock(JavaPlugin.class);
    final VehicleModule module = mock(VehicleModule.class);
    final FloatingMenuService menus = mock(FloatingMenuService.class);
    final List<Mesh> meshes = new ArrayList<>();
    final VehicleDefinition definition = VehicleFixtures.definition(VehicleDefinition.Kind.CAR);
    final AtomicReference<Location> feet = new AtomicReference<>(new Location(world, 0, 64, 0));
    final AtomicReference<MenuInputForm> form = new AtomicReference<>();
    final VehicleModelEditor editor;
    final LanguageService language;
    final AtomicReference<VehicleDefinition> saved = new AtomicReference<>();
    private final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
    private Handle current;
    MenuScene scene;

    VehicleEditorHarness() throws Exception { this(Language.DEFAULT); }
    VehicleEditorHarness(Language locale) throws Exception {
        try {
            when(plugin.getName()).thenReturn("mik");
            when(plugin.namespace()).thenReturn("mik");
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            var server = mock(Server.class); when(plugin.getServer()).thenReturn(server);
            UUID id = UUID.randomUUID(); when(player.getUniqueId()).thenReturn(id);
            when(server.getPlayer(id)).thenReturn(player);
            when(player.isOnline()).thenReturn(true); when(player.hasPermission(anyString())).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenAnswer(_ -> feet.get().clone());
            when(player.getEyeLocation()).thenAnswer(_ -> feet.get().clone().add(0, 1.62, 0));
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
            when(module.editorOrigin(player)).thenAnswer(_ -> feet.get().clone());
            when(module.models()).thenReturn(Map.of(definition.id(), definition));
            when(module.saveModel(any())).thenAnswer(invocation -> {
                var value = invocation.getArgument(0, VehicleModelDraft.class).validate(); saved.set(value); return value;
            });
            var block = mock(BlockData.class); when(block.getAsString()).thenReturn("minecraft:stone");
            bukkit.when(() -> Bukkit.createBlockData(anyString())).thenReturn(block);
            when(world.spawn(any(Location.class), eq(BlockDisplay.class), org.mockito.ArgumentMatchers.<Consumer<BlockDisplay>>any())).thenAnswer(invocation -> {
                var mesh = new Mesh(mock(BlockDisplay.class), invocation.getArgument(0)); meshes.add(mesh);
                invocation.<Consumer<BlockDisplay>>getArgument(2).accept((BlockDisplay) mesh.display);
                return mesh.display;
            });
            when(world.spawn(any(Location.class), eq(TextDisplay.class), org.mockito.ArgumentMatchers.<Consumer<TextDisplay>>any())).thenAnswer(invocation -> {
                var mesh = new Mesh(mock(TextDisplay.class), invocation.getArgument(0)); meshes.add(mesh);
                invocation.<Consumer<TextDisplay>>getArgument(2).accept((TextDisplay) mesh.display);
                return mesh.display;
            });
            var bundle = FluentTestSupport.bundle(locale);
            language = mock(LanguageService.class, invocation -> {
                Object[] args = invocation.getRawArguments();
                if (invocation.getMethod().getName().equals("text"))
                    return Component.text(FluentTestSupport.text(bundle, (Message) args[1], (Object[]) args[3]));
                if (invocation.getMethod().getName().equals("t"))
                    return FluentTestSupport.text(bundle, (Message) args[1], (Object[]) args[2]);
                return RETURNS_DEFAULTS.answer(invocation);
            });
            when(menus.open(eq(player), any())).thenAnswer(invocation -> {
                scene = invocation.getArgument(1); current = new Handle(); return current;
            });
            when(menus.current(player)).thenAnswer(_ -> current == null || current.state == FloatingMenuState.CLOSED ? Optional.empty() : Optional.of(current));
            when(menus.worldOverlayVisibility(player)).thenReturn(_ -> true);
            doAnswer(invocation -> { invocation.getArgument(0, Runnable.class).run(); return null; }).when(menus).execute(any());
            doAnswer(invocation -> { form.set(invocation.getArgument(1)); return null; }).when(menus).input(eq(player), any());
            FloatingMenus.install(menus);
            editor = new VehicleModelEditor(plugin, language, module);
            editor.enable();
        } catch (Exception | Error failure) {
            FloatingMenus.uninstall(); bukkit.close(); throw failure;
        }
    }

    void edit() throws Exception { editor.edit(player, definition.id()); editor.open(player); }
    void aimHandle(String key) {
        var preview = editor.preview(player);
        var handle = preview.handles().stream().filter(value -> value.key().equals(key)).findFirst().orElseThrow();
        lookAt(VehicleEditorGeometry.world(preview.origin(), handle.point().multiply(preview.scale())));
    }
    void click(String key) {
        for (int pass = 0; pass < 32; pass++) {
            var frame = SceneGeometryTestSupport.measure(scene, 1);
            var target = frame.elements().stream().filter(element -> element.node().key().value().equals(key)).findFirst();
            if (target.isPresent()) {
                target.get().node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(player, current, FloatingMenuInteraction.PRIMARY);
                return;
            }
            var next = frame.pagination().stream().filter(page -> page.page().hasNext()).findFirst();
            if (next.isEmpty()) throw new AssertionError("Missing control " + key + ": " + frame.elements().stream().map(element -> element.node().key().value()).toList());
            next.get().action(FloatingMenuInteraction.SCROLL_DOWN).execute(player, current, FloatingMenuInteraction.SCROLL_DOWN);
        }
        throw new AssertionError("Control remained outside the reading window: " + key);
    }
    void advance(int count) { for (int index = 0; index < count; index++) editor.tick(); }
    void lookAt(Location point) {
        var eye = point.clone().add(0, 0, -3);
        eye.setDirection(point.toVector().subtract(eye.toVector()));
        feet.set(eye.subtract(0, 1.62, 0));
    }
    List<Mesh> active() { return meshes.stream().filter(mesh -> mesh.valid).toList(); }
    Mesh mesh() { return active().stream().filter(mesh -> mesh.display instanceof BlockDisplay).findFirst().orElseThrow(); }
    @Override public void close() { try { editor.close(); FloatingMenus.uninstall(); } finally { bukkit.close(); } }

    final class Handle implements FloatingMenuHandle {
        final UUID id = UUID.randomUUID();
        FloatingMenuState state = FloatingMenuState.ACTIVE;
        @Override public UUID id() { return id; }
        @Override public UUID playerId() { return player.getUniqueId(); }
        @Override public FloatingMenuState state() { return state; }
        @Override public int depth() { return 0; }
        @Override public void refresh() { }
        @Override public void reanchor() { }
        @Override public void update(MenuScene value) { scene = value; }
        @Override public FloatingMenuHandle openChild(MenuScene value) { scene = value; return this; }
        @Override public void openPopover(org.encinet.mik.shared.menu.ui.UiKey key, java.util.function.Function<Runnable, MenuPanel> content) { }
        @Override public void openPopover(MenuPopover value) { }
        @Override public void closePopover() { }
        @Override public void closePopover(MenuPopover expected) { }
        @Override public void back() { close(); }
        @Override public void close() { state = FloatingMenuState.CLOSED; }
        @Override public void feedback(Component message, FloatingMenuFeedbackKind kind) { }
    }

    static final class Mesh {
        final Display display;
        final AtomicReference<Location> position;
        final AtomicReference<Matrix4f> matrix = new AtomicReference<>(new Matrix4f());
        final AtomicReference<Component> text = new AtomicReference<>(Component.empty());
        boolean valid = true;
        boolean glowing;
        Mesh(Display display, Location location) {
            this.display = display; position = new AtomicReference<>(location.clone());
            when(display.getUniqueId()).thenReturn(UUID.randomUUID());
            when(display.isValid()).thenAnswer(_ -> valid);
            when(display.getWorld()).thenReturn(location.getWorld());
            when(display.getLocation()).thenAnswer(_ -> position.get().clone());
            when(display.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            when(display.getTransformation()).thenAnswer(_ -> new org.bukkit.util.Transformation(
                    matrix.get().getTranslation(new org.joml.Vector3f()), matrix.get().getUnnormalizedRotation(new org.joml.Quaternionf()),
                    matrix.get().getScale(new org.joml.Vector3f()), new org.joml.Quaternionf()));
            when(display.isGlowing()).thenAnswer(_ -> glowing);
            doAnswer(invocation -> { glowing = invocation.getArgument(0); return null; }).when(display).setGlowing(anyBoolean());
            doAnswer(_ -> { valid = false; return null; }).when(display).remove();
            when(display.teleport(any(Location.class))).thenAnswer(invocation -> { position.set(invocation.getArgument(0)); return true; });
            doAnswer(invocation -> { matrix.set(new Matrix4f(invocation.getArgument(0, Matrix4f.class))); return null; }).when(display).setTransformationMatrix(any());
            if (display instanceof TextDisplay label) {
                when(label.text()).thenAnswer(_ -> text.get());
                doAnswer(invocation -> { text.set(invocation.getArgument(0)); return null; }).when(label).text(any(Component.class));
            }
        }
    }
}
