package org.encinet.mik.feature.world.heads;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.encinet.mik.feature.world.heads.catalog.HeadCatalog;
import org.encinet.mik.feature.world.heads.catalog.HeadEntry;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.FloatingMenuScale;
import org.encinet.mik.shared.menu.FloatingMenuSize;
import org.encinet.mik.shared.menu.FloatingMenuTextScale;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.runtime.input.MenuPaginationInput;
import org.encinet.mik.shared.menu.runtime.layout.FloatingMenuNodeGeometry;
import org.encinet.mik.shared.menu.runtime.layout.MenuViewportLayout;
import org.encinet.mik.shared.menu.runtime.layout.SceneGeometry;
import org.encinet.mik.shared.menu.runtime.space.FloatingMenuSurfaceGeometry;
import org.encinet.mik.shared.menu.ui.Ui;
import org.encinet.mik.shared.menu.ui.UiContent;
import org.encinet.mik.shared.menu.ui.UiFrame;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.menu.ui.UiNode;
import org.encinet.mik.shared.menu.ui.UiPalette;
import org.encinet.mik.shared.menu.ui.UiSize;
import org.encinet.mik.shared.menu.ui.UiVector3;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeadScenesTest {
    @Test void galleryKeepsModelsAndBothActionsAtEveryLanguageAndMaximumText() throws Exception {
        try (var stacks = headModels()) {
            var entries = heads();
            for (Language locale : Language.values()) {
                var scenes = scenes(locale);
                var position = new AtomicInteger();
                var detail = new AtomicReference<String>();
                var favorite = new AtomicReference<String>();
                var actions = new HeadScenes.DirectoryActions(_ -> fail(), position::set, (_, _) -> fail(), () -> fail(),
                        (_, head) -> detail.set(head.id()), (_, head) -> favorite.set(head.id()));
                var scene = directory(scenes, entries, position.get(), actions);
                var frame = narrow(scene);
                assertDisjoint(frame);
                assertNotNull(element(frame, "back"));
                assertNotNull(element(frame, "filters"));
                assertTrue(element(frame, "query").node().input().interactive());
                assertTrue(cards(frame).size() >= 4, locale.name());
                var first = element(frame, "1");
                assertInstanceOf(UiContent.Item.class, first.node().content(), locale.name());
                assertTrue(first.node().input().selected());
                assertTrue(plain(first.node().content().label()).startsWith("★ "));
                assertTrue(plain(first.node().input().tooltip().orElseThrow().text()).contains(entries.getFirst().name()));
                execute(first, FloatingMenuInteraction.PRIMARY);
                execute(first, FloatingMenuInteraction.SECONDARY);
                assertEquals("1", detail.get(), locale.name());
                assertEquals("1", favorite.get(), locale.name());
                assertEquals(1, frame.elements().stream().filter(element -> element.node().key().equals(UiKey.item("1"))).count());
                var page = frame.pagination().getFirst();
                assertTrue(page.page().hasNext());
                page.action(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
                assertEquals(page.page().toIndex(), position.get());
                var next = narrow(directory(scenes, entries, position.get(), actions));
                assertDisjoint(next);
                assertNotNull(element(next, Integer.toString(position.get() + 1)));
                assertEquals(element(frame, "back").y(), element(next, "back").y(), 1e-6);
                assertEquals(element(frame, "next").x(), element(next, "next").x(), 1e-6);
            }
            assertFalse(stacks.constructed().isEmpty());
        }
    }

    @Test void galleryUsesAlignedTilesAndRetainsPreviewsWhenHeightForcesCompactCards() throws Exception {
        try (var stacks = headModels()) {
            var scenes = scenes(Language.EN_US);
            var position = new AtomicInteger();
            var actions = new HeadScenes.DirectoryActions(_ -> fail(), position::set,
                    (_, _) -> fail(), () -> fail(), (_, _) -> fail(), (_, _) -> fail());
            var scene = directory(scenes, heads(), 0, actions);
            var view = SceneGeometryTestSupport.viewingFrame(scene, 1, FloatingMenuFieldOfView.DEFAULT, 1);
            var full = view.frame();
            assertDisjoint(full);
            var cards = cards(full);
            assertEquals(40, cards.size());
            assertUniformGrid(cards);
            var regular = (UiContent.Item) cards.getFirst().node().content();
            var crammed = SceneGeometryTestSupport.measure(scene.toBuilder().viewport(new UiSize(690, 190)).build(), 1);
            assertDisjoint(crammed);
            var compact = assertInstanceOf(UiContent.Item.class, element(crammed, "1").node().content());
            assertTrue(compact.modelSize().value() < regular.modelSize().value());
            execute(element(crammed, "next"), FloatingMenuInteraction.PRIMARY);
            assertEquals(crammed.pagination().getFirst().page().toIndex(), position.get());
            assertFalse(stacks.constructed().isEmpty());
        }
    }

    @Test void galleryWingsKeepModelsAndCaptionsClickableAndPageTogether() throws Exception {
        try (var stacks = headModels()) {
            var scenes = scenes(Language.EN_US);
            var position = new AtomicInteger();
            var selected = new AtomicReference<String>();
            var actions = new HeadScenes.DirectoryActions(_ -> fail(), position::set, (_, _) -> fail(), () -> fail(),
                    (_, head) -> selected.set(head.id()), (_, _) -> fail());
            var scene = directory(scenes, heads(), 0, actions);
            var fov = FloatingMenuFieldOfView.DEFAULT;
            var geometry = SceneGeometry.measure(scene, 1, fov, 1);
            var frame = geometry.frame();
            assertEquals(1, frame.pagination().size());
            assertEquals(2, geometry.pagination().stream().filter(region -> Math.abs(region.pose().yawDegrees()) > 15).count());
            assertEquals(0, geometry.poses().get(element(frame, "4").path()).yawDegrees(), 1e-6);
            assertTrue(geometry.poses().get(element(frame, "1").path()).yawDegrees() < -15);
            assertTrue(geometry.poses().get(element(frame, "8").path()).yawDegrees() > 15);
            assertEquals(geometry.poses().get(element(frame, "1").path()).yawDegrees(),
                    geometry.poses().get(element(frame, "2").path()).yawDegrees(), 1e-6);

            double distance = MenuViewportLayout.distance(fov);
            double scale = Math.min(MenuViewportLayout.scale(1), geometry.bounds().readableScale(distance, 0, scene.readingFraming(fov)));
            var eye = new Vector(0, 0, distance);
            for (String key : List.of("1", "8", "33", "40")) {
                var card = element(frame, key);
                var pose = geometry.poses().get(card.path());
                var entry = geometry.definition().entries().get(card.path());
                var part = FloatingMenuNodeGeometry.placement(entry.style(),
                        geometry.measurements().get(card.path()).captionHeight(), scale, 0, entry.modelSize());
                for (var offset : List.of(new UiVector3(0, part.visualUp(), part.visualForward()),
                        new UiVector3(0, part.textUp(), part.textForward()))) {
                    var ray = point(pose, scale).add(vector(pose.rotation().apply(offset))).subtract(eye);
                    var hit = frame.elements().stream().filter(node -> node.node().input().interactive())
                            .flatMap(node -> intersect(eye, ray, geometry.poses().get(node.path()), geometry.sizes().get(node.path()), scale)
                                    .stream().map(target -> Map.entry(node, target.distance())))
                            .min(Comparator.comparingDouble(Map.Entry::getValue)).orElseThrow().getKey();
                    execute(hit, FloatingMenuInteraction.PRIMARY);
                    assertEquals(key, selected.get());
                }
                var paging = MenuPaginationInput.action(geometry.pagination(), card.path(), "", FloatingMenuInteraction.SCROLL_DOWN,
                        _ -> Optional.empty());
                assertNotNull(paging);
                paging.execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
                assertEquals(40, position.get());
            }
            var next = SceneGeometry.measure(directory(scenes, heads(), position.get(), actions), 1, fov, 1);
            assertEquals(20, cards(next.frame()).size());
            assertEquals(frame.size(), next.frame().size());
            assertEquals(element(frame, "back").y(), element(next.frame(), "back").y(), 1e-6);
            assertNotNull(element(next.frame(), "previous"));
            assertFalse(stacks.constructed().isEmpty());
        }
    }

    @Test void filterAndDetailControlsRemainSeparateAtLargeText() throws Exception {
        try (var stacks = headModels()) {
            var scenes = scenes(Language.DE_DE);
            var filter = new HeadScenes.FilterView(search(), HeadCatalog.Facet.TAG,
                    List.of(new HeadCatalog.Label("home", "Ein besonders langer Name für den Garten", "Gartendekoration")),
                    0, Set.of("home"), 1, HeadCatalog.Sort.NAME, true);
            var filterActions = new HeadScenes.FilterActions(_ -> fail(), _ -> fail(), _ -> fail(), () -> fail(), () -> fail(), () -> fail());
            var filterFrame = narrow(scenes.filters(filter, filterActions));
            assertDisjoint(filterFrame);
            assertTrue(element(filterFrame, "home").node().input().selected());
            assertNotNull(element(filterFrame, "clear-filters"));
            assertNotNull(element(filterFrame, "back"));

            var head = new HeadEntry("1", "Ein besonders langer Name für einen dekorativen Kopf", null,
                    "garden", Set.of("outdoors", "shelf"), Set.of("summer"));
            var catalog = mock(HeadCatalog.class);
            when(catalog.label(HeadCatalog.Facet.CATEGORY, "garden")).thenReturn("Dekoration für den Garten und den Eingangsbereich");
            when(catalog.label(HeadCatalog.Facet.TAG, "outdoors")).thenReturn("Dekoration für den Außenbereich");
            when(catalog.label(HeadCatalog.Facet.TAG, "shelf")).thenReturn("Pflanzen für ein besonders großes Regal");
            when(catalog.label(HeadCatalog.Facet.COLLECTION, "summer")).thenReturn("Sommerkollektion für Hinterhöfe und Gartenhäuser");
            var detail = new HeadScenes.Detail(head, catalog, head.name(), 64, true, null, null);
            var amount = new AtomicInteger();
            var claims = new AtomicInteger();
            var scene = scenes.detail(detail, amount::set, (_, _) -> claims.incrementAndGet(), (_, _) -> fail());
            var detailFrame = narrow(scene);
            assertDisjoint(detailFrame);
            var preview = element(detailFrame, "preview");
            assertFalse(preview.node().input().target());
            assertNotNull(element(detailFrame, "back"));
            var tags = element(detailFrame, "tag");
            assertTrue(plain(tags.node().input().tooltip().orElseThrow().text()).contains("besonders großes Regal"));
            assertTrue(element(detailFrame, "track").y() > preview.y() + preview.size().height());
            var take = element(detailFrame, "take");
            assertTrue(take.y() > element(detailFrame, "track").y());
            execute(take, FloatingMenuInteraction.PRIMARY);
            assertEquals(1, claims.get());
            assertNotNull(element(detailFrame, "favorite"));
            assertTrue(element(detailFrame, "64").node().input().selected());
            execute(element(detailFrame, "16"), FloatingMenuInteraction.PRIMARY);
            assertEquals(16, amount.get());
            execute(element(detailFrame, "track"), FloatingMenuInteraction.SCROLL_DOWN);
            assertEquals(63, amount.get());
            assertFalse(stacks.constructed().isEmpty());
        }
    }

    private static MockedConstruction<ItemStack> headModels() {
        return mockConstruction(ItemStack.class, (stack, _) -> {
            when(stack.getType()).thenReturn(Material.PLAYER_HEAD);
            when(stack.clone()).thenReturn(stack);
        });
    }

    private static List<HeadEntry> heads() {
        return IntStream.rangeClosed(1, 60).mapToObj(index -> HeadEntry.saved(Integer.toString(index),
                "Ein langer Name für eine Dekoration · 庭院装饰 " + index)).toList();
    }

    private static HeadScenes scenes(Language locale) throws Exception {
        var bundle = FluentTestSupport.bundle(locale);
        var player = mock(Player.class);
        var languages = mock(LanguageService.class);
        when(languages.t(eq(player), any(Message.class), any(Object[].class))).thenAnswer(call ->
                FluentTestSupport.text(bundle, (Message) call.getRawArguments()[1], (Object[]) call.getRawArguments()[2]));
        return new HeadScenes(languages, player);
    }

    private static MenuScene directory(HeadScenes scenes, List<HeadEntry> entries, int position, HeadScenes.DirectoryActions actions) {
        return scenes.directory(new HeadScenes.Directory(HeadScenes.Section.CATALOG, true, search(), entries, position,
                null, false, false, 2, Set.of("1")), actions).build();
    }

    private static UiNode search() {
        return Ui.textField("").previewKey(UiKey.named("query"))
                .placeholder(Component.text("Suchbegriff oder ID eingeben")).primary((_, _) -> fail()).build();
    }

    private static UiFrame narrow(MenuScene scene) {
        return SceneGeometry.measureBase(scene, FloatingMenuTextScale.MAXIMUM.factor(), new FloatingMenuFieldOfView(30),
                FloatingMenuScale.MAXIMUM.factor(), UiPalette.DEFAULT).frame();
    }

    private static List<UiFrame.Element> cards(UiFrame frame) {
        return frame.elements().stream().filter(element -> element.node().content() instanceof UiContent.Item).toList();
    }

    private static Vector point(FloatingMenuPose pose, double scale) {
        return new Vector(pose.right(), pose.up(), pose.forward()).multiply(scale);
    }

    private static Vector vector(UiVector3 vector) {
        return new Vector(vector.right(), vector.up(), vector.forward());
    }

    private static Optional<FloatingMenuSurfaceGeometry.Hit> intersect(Vector eye, Vector ray, FloatingMenuPose pose,
                                                                       FloatingMenuSize size, double scale) {
        return FloatingMenuSurfaceGeometry.intersect(eye, ray, point(pose, scale), (float) pose.yawDegrees(),
                (float) pose.pitchDegrees(), (float) pose.rollDegrees(), size.width() * scale, size.height() * scale, 0);
    }

    private static void execute(UiFrame.Element element, FloatingMenuInteraction interaction) {
        element.node().input().actions().get(interaction).execute(null, null, interaction);
    }

    private static String plain(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }
}
