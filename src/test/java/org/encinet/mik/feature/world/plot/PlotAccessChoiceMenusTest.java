package org.encinet.mik.feature.world.plot;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PlotAccessChoiceMenusTest {
    @Test void pagingTheChoiceRetainsTheInvokingTableWhileANewSubjectStartsAtTheBeginning() {
        var original = PlotAccessPolicy.Subject.group(PlotAccessPolicy.Group.ADMIN);
        var other = PlotAccessPolicy.Subject.group(PlotAccessPolicy.Group.MEMBER);
        var target = new PlotAccessChoiceMenus.Subject(new UUID(0, 1), original, PlotPermission.Category.MANAGEMENT);
        var state = new PlotAccessChoiceMenus.State(target, 0, 5);
        for (int page : new int[]{2, 1, 0, 7}) {
            state = state.reading(page);
            assertEquals(page, state.page()); assertEquals(5, state.tablePage(original));
            assertEquals(0, state.tablePage(other)); assertSame(target, state.target());
        }
    }

    @Test void allChoicesRemainReachableWithTheirSelectionAndFullSourceAtReadingExtremes() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            for (double font : new double[]{.7, 1.8}) for (double scale : new double[]{.7, 1.3}) for (int fov : new int[]{30, 110}) {
                for (boolean settings : new boolean[]{true, false}) {
                    var labels = new ArrayList<Component>();
                    if (settings) for (var setting : PlotAccessPolicy.Setting.values()) {
                        String result = FluentTestSupport.text(bundle, setting == PlotAccessPolicy.Setting.DENY ? Message.PLOT_DENIED : Message.PLOT_ALLOWED);
                        String status = setting == PlotAccessPolicy.Setting.DEFAULT ? FluentTestSupport.text(bundle, Message.PLOT_PERMISSION_DEFAULT_STATUS, result) : result;
                        labels.add(Component.text(status + "\n" + FluentTestSupport.text(bundle, Message.PLOT_PERMISSION_SOURCE_PARENT)
                                + " · " + "Long inherited plot 👩‍👩‍👧‍👦 e\u0301 ".repeat(5)));
                    }
                    else for (var message : List.of(Message.PLOT_ROLE_OWNER, Message.PLOT_ROLE_ADMIN, Message.PLOT_MENU_GROUP_COLLABORATOR,
                            Message.PLOT_MENU_GROUP_NEWCOMER, Message.PLOT_MENU_GROUP_MEMBER, Message.PLOT_MENU_MEMBERS))
                        labels.add(Component.text(message == Message.PLOT_MENU_MEMBERS ? FluentTestSupport.text(bundle, message, 300)
                                : FluentTestSupport.text(bundle, message)));
                    var pageIndex = new AtomicInteger(); var invoked = new AtomicInteger(-1); var seen = new HashSet<Integer>();
                    for (int pass = 0; pass < labels.size(); pass++) {
                        var menu = PlotMenuLayouts.screen("choice", FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.choices(pageIndex.get(), pageIndex::set));
                        menu.information("heading", Component.text(FluentTestSupport.text(bundle, Message.PLOT_PERMISSION_SWITCH))).region("heading");
                        for (int index = 0; index < labels.size(); index++) {
                            int choice = index;
                            var node = menu.control("choice:" + index, labels.get(index)).region("rows").selected(index == 1)
                                    .primary((_, _) -> invoked.set(choice));
                            if (index == 0) node.disabled(Component.text("Permission changed"));
                        }
                        menu.back(Component.text(FluentTestSupport.text(bundle, Message.BACK))).region("navigation");
                        var scene = menu.build();
                        var frame = SceneGeometryTestSupport.readableFrame(scene, font, new FloatingMenuFieldOfView(fov), scale);
                        assertDisjoint(frame); assertNotNull(element(frame, "back"));
                        assertEquals(-1, invoked.get(), "Paging and selection must not execute the choice");
                        for (var item : frame.elements()) if (item.node().key().value().startsWith("choice:")) {
                            int choice = Integer.parseInt(item.node().key().value().substring(7));
                            assertTrue(seen.add(choice)); assertEquals(choice == 1, item.node().input().selected());
                            assertEquals(choice != 0, item.node().input().enabled());
                            assertFullTextAvailable(labels.get(choice),item);
                        }
                        var page = frame.pagination().getFirst();
                        if (!page.page().hasNext()) break;
                        page.action(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
                    }
                    assertEquals(labels.size(), seen.size(), language + " @ " + font + " FOV=" + fov);
                }
            }
        }
    }

    @Test void subjectsPermissionsAndPlotsCannotReuseAnotherChoicesInputPath() {
        var paths = new HashSet<String>();
        for (var plot : List.of(new UUID(0, 1), new UUID(0, 2))) for (var group : PlotAccessPolicy.Group.values()) {
            var subject = PlotAccessPolicy.Subject.group(group);
            var targets = new ArrayList<PlotAccessChoiceMenus.Target>();
            targets.add(new PlotAccessChoiceMenus.Subject(plot, subject, PlotPermission.Category.MANAGEMENT));
            for (var permission : PlotPermission.Category.MANAGEMENT.permissions())
                targets.add(new PlotAccessChoiceMenus.Setting(plot, subject, permission, permission.category()));
            for (var target : targets) {
                var menu = PlotMenuLayouts.screen(target.scope(), FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.choices(0, _ -> { }));
                menu.control("choose", Component.text("Choose")).region("rows");
                assertTrue(paths.add(entry(menu.build(), "choose").id()), target.toString());
            }
        }
    }
}
