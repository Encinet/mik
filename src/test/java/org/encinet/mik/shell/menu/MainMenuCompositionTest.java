package org.encinet.mik.shell.menu;

import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.feature.world.plot.PlotNoticeBoard;
import org.encinet.mik.feature.world.plot.PlotNoticeTestSupport;
import org.encinet.mik.feature.community.communication.AnnouncementPreviewTestSupport;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceIdentityTestSupport;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MainMenuCompositionTest {
    private static final Component AVATAR = Component.object(ObjectContents.playerHead(UUID.fromString("00000000-0000-0000-0000-000000000001")));

    @Test void personalContextAndExternalLinksStayOnOppositeSidesOfTheLauncher() {
        var scene = fixture(content(profile().content(Component.text("Identity details"), (_, _) -> {}), null, null),
                "heading", "destinations", "links-heading", "links", "quick", "footer");
        var frame = frame(scene);
        assertDisjoint(frame);
        assertTrue(element(frame, "play-time").x() < element(frame, "heading").x());
        assertTrue(element(frame, "links").x() > element(frame, "heading").x());
        assertTrue(element(frame, "quick").y() > element(frame, "destinations").y());
        assertTrue(element(frame, "footer").y() > element(frame, "quick").y());
        assertTrue(element(frame, "identity").y() > element(frame, "player-name").y());
        assertTrue(element(frame, "play-time").y() > element(frame, "identity").y());
        var avatar = element(frame, "avatar").node();
        assertInstanceOf(UiContent.Text.class, avatar.content());
        assertInstanceOf(ObjectComponent.class, ((UiContent.Text) avatar.content()).label());
        assertFalse(avatar.input().target());
    }

    @Test void sidebarsAndQuickControlsFoldTowardsThePlayerAroundTheFlatLauncher() {
        var scene = fixture(content(profile().content(Component.text("Identity details"), (_, _) -> {}), null, null),
                "heading", "destinations", "links-heading", "links", "quick", "footer");
        for (double typography : new double[]{.7, 1, 1.8}) {
            assertTrue(SceneGeometryTestSupport.pose(scene, typography, "play-time").yawDegrees() < 0);
            assertTrue(SceneGeometryTestSupport.pose(scene, typography, "links").yawDegrees() > 0);
            assertTrue(SceneGeometryTestSupport.pose(scene, typography, "quick").pitchDegrees() < 0);
            assertEquals(0, SceneGeometryTestSupport.pose(scene, typography, "play-time").pitchDegrees(), 1e-8);
            assertEquals(0, SceneGeometryTestSupport.pose(scene, typography, "links").pitchDegrees(), 1e-8);
            assertEquals(0, SceneGeometryTestSupport.pose(scene, typography, "quick").yawDegrees(), 1e-8);
            assertEquals(0, SceneGeometryTestSupport.pose(scene, typography, "heading").yawDegrees(), 1e-8);
            assertEquals(0, SceneGeometryTestSupport.pose(scene, typography, "heading").pitchDegrees(), 1e-8);
        }
    }

    @Test void translatedWidgetPagesKeepEveryCardAndPersistentActionReachable() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            BiFunction<Message,Object[],Component> text = (message,args) -> Component.text(FluentTestSupport.text(bundle,message,args));
            for (var scale : FloatingMenuTextScale.values()) for (boolean notice : new boolean[]{false,true})
                for (boolean pvp : new boolean[]{false,true}) for (boolean afk : new boolean[]{false,true}) {
                    var profile = profile(text);
                    var profileNode = profile.content(text.apply(Message.GOVERNANCE_MENU_IDENTITY_HINT,new Object[0]),(_,_) -> {});
                    var plot = notice ? PlotNoticeTestSupport.preview(notice("社区花园公告。\n".repeat(40)),
                            text.apply(Message.PLOT_NOTICE_READ,new Object[0]),(_,_) -> {}) : null;
                    var announcement = AnnouncementPreviewTestSupport.content(language,"服务器公告：欢迎参与社区建设。\n".repeat(20));
                    var builder = launcher(text,pvp,afk);
                    var seen = new java.util.HashSet<String>();
                    for (var page : widgetPages(builder,profileNode,plot,null,announcement,scale.factor())) {
                        var scene = page.scene(); var frame = page.frame();
                        assertDisjoint(frame); assertDestinationReadingOrder(frame,language+" @ "+scale);
                        assertTrue(frame.size().width() <= MenuScene.DEFAULT_VIEWPORT.width()+1e-6);
                        assertTrue(frame.size().height() <= MenuScene.DEFAULT_VIEWPORT.height()+1e-6);
                        assertNotNull(element(frame,"close")); assertNotNull(element(frame,"settings"));
                        assertEquals(pvp,element(frame,"pvp-toggle").node().input().selected());
                        assertEquals(afk,element(frame,"afk").node().input().selected());
                        SceneGeometryTestSupport.assertVisualContentsFit(scene,scale.factor());
                        for (var panel : frame.panels()) for (String id : List.of("profile","announcements","links","plot"))
                            if (panel.path().endsWith("/"+id)) { seen.add(id); assertTrue(panel.size().width() <= 300+1e-6); }
                        if (visible(frame,"play-time")) {
                            assertEquals(UiRole.NAVIGATION,element(frame,"identity").node().role());
                            for (var fact : profile.facts()) {
                                assertFalse(entry(scene,fact.key()).interactive());
                                assertEquals(element(frame,"play-time").x(),element(frame,fact.key()).x(),1e-6);
                            }
                        }
                        if (visible(frame,"announcement-body")) {
                            var body = element(frame,"announcement-body");
                            var source = entry(scene,"announcement-body").label();
                            assertFullTextAvailable(source,body);
                            assertEquals(!body.node().content().label().equals(source),visible(frame,"announcement-read"));
                        }
                        if (visible(frame,"notice-body")) {
                            assertEquals(UiRole.HEADING,element(frame,"plot-name").node().role());
                            var body = element(frame,"notice-body");
                            var source = entry(scene,"notice-body").label();
                            assertFullTextAvailable(source,body);
                            assertEquals(!body.node().content().label().equals(source),visible(frame,"notice-read"));
                            if (!pvp && !afk && visible(frame,"notice-read")) {
                                var reader = PlotNoticeTestSupport.reading(notice("社区花园完整公告 🌿\n".repeat(60)),UiReadingPosition.START,_ -> {});
                                var companion = scene.toBuilder().popover(MenuPopover.attached(UiKey.named("notice-read"),
                                        text.apply(Message.PLOT_NOTICE_BOARD,new Object[0]),reader,text.apply(Message.CLOSE,new Object[0]))).build();
                                for (int fov : new int[]{30,70,110}) {
                                    var popup = SceneGeometryTestSupport.popoverFrame(companion,scale.factor(),new FloatingMenuFieldOfView(fov));
                                    assertNotNull(popup); assertDisjoint(popup);
                                }
                            }
                        }
                        if (!notice && !pvp && !afk && visible(frame,"identity")) {
                            var identity = GovernanceIdentityTestSupport.content(language,(message,args) -> FluentTestSupport.text(bundle,message,args),false);
                            var companion = scene.toBuilder().popover(MenuPopover.attached(UiKey.named("identity"),Component.text("Details"),identity,Component.text("Close"))).build();
                            for (int fov : new int[]{30,70,110}) {
                                var popup = SceneGeometryTestSupport.popoverFrame(companion,scale.factor(),new FloatingMenuFieldOfView(fov));
                                assertNotNull(popup); assertDisjoint(popup);
                            }
                        }
                    }
                    assertEquals(notice ? java.util.Set.of("profile","announcements","links","plot")
                            : java.util.Set.of("profile","announcements","links"),seen);
                }
        }
    }

    @Test void allFactsAreVisibleWithoutAnotherActionAndOnlyIdentityNavigates() {
        var opened = new java.util.concurrent.atomic.AtomicInteger();
        var profile = profile();
        var scene = MenuScene.screen("profile").content(profile.content(Component.text("Promotion and activity"),
                (_, _) -> opened.incrementAndGet())).build();
        assertEquals(1, scene.entries().values().stream().filter(MenuScene.Entry::interactive).count());
        for (var fact : profile.facts()) assertNotNull(entry(scene, fact.key()));
        entry(scene, "identity").trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals(1, opened.get());
    }

    @Test void shortNoticesShowAllContentAndOnlyTruncatedNoticesOfferFullReading() {
        var opened = new java.util.concurrent.atomic.AtomicInteger();
        String shortBody = "欢迎来到花园。\n请爱护花草，欢迎一起建设。";
        var shortScene = noticeScene(shortBody, (_, _) -> opened.incrementAndGet());
        var shortFrame = SceneGeometryTestSupport.measure(shortScene, 1);
        assertEquals(notice(shortBody).name(), PlainTextComponentSerializer.plainText().serialize(entry(shortScene, "plot-name").label()));
        assertEquals(1, shortFrame.elements().stream().filter(element -> element.node().role() == UiRole.HEADING
                && element.path().contains("/plot/")).count());
        assertEquals(shortBody, PlainTextComponentSerializer.plainText().serialize(element(shortFrame, "notice-body").node().content().label()));
        assertTrue(shortFrame.elements().stream().noneMatch(element -> element.node().key().value().equals("notice-read")));
        assertFalse(entry(shortScene, "notice-body").interactive());
        String longBody = "社区建设公告：欢迎参与。\n".repeat(40);
        var longCard = PlotNoticeTestSupport.preview(notice(longBody),Component.text("查看全文"),(_,_) -> opened.incrementAndGet());
        var pages = widgetPages(fixtureBuilder(Ui.column().build(),"heading","destinations","links-heading","links","quick","footer")
                        .viewport(MenuScene.DEFAULT_VIEWPORT),profile().content(Component.text("Identity"),(_,_) -> {}),longCard,null,null,1);
        var visible = pages.stream().filter(page -> visible(page.frame(),"notice-body")).findFirst().orElseThrow();
        var longScene = visible.scene();
        var longFrame = visible.frame();
        String preview = PlainTextComponentSerializer.plainText().serialize(element(longFrame, "notice-body").node().content().label());
        assertFullTextAvailable(Component.text(longBody,net.kyori.adventure.text.format.NamedTextColor.WHITE),element(longFrame,"notice-body"));
        if (!preview.equals(longBody)) {
            assertTrue(preview.endsWith("…"));
            assertTrue(longBody.startsWith(preview.substring(0, preview.length() - 1)));
        }
        assertEquals(!preview.equals(longBody),visible(longFrame,"notice-read"));
        assertTrue(preview.contains("\n"));
        assertNull(entry(longScene, "notice-body").tooltip());
        entry(longScene, "notice-read").trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals(1, opened.get());
        for (var scale : FloatingMenuTextScale.values()) {
            assertDisjoint(SceneGeometryTestSupport.measure(shortScene, scale.factor()));
            assertDisjoint(SceneGeometryTestSupport.measure(longScene, scale.factor()));
            assertFullTextAvailable(entry(longScene,"notice-body").label(),element(SceneGeometryTestSupport.measure(longScene,scale.factor()),"notice-body"));
        }
    }

    @Test void missingOrClearedNoticesDoNotLeaveAnEmptyCard() {
        assertTrue(MenuScene.screen("empty-notice").content(PlotNoticeTestSupport.preview(null, Component.text("Read all"), (_,_) -> {})).build().entries().isEmpty());
        for (String body : List.of("", " \n  ")) {
            var scene = noticeScene(body, (_,_) -> {});
            assertNull(entry(scene, "notice-body"));
            assertTrue(SceneGeometryTestSupport.measure(scene, 1).panels().stream()
                    .noneMatch(panel -> panel.path().endsWith("/plot")));
        }
    }

    @Test void changingLiveValuesKeepsTheLauncherInPlace() {
        var shortProfile = profile();
        var facts = new java.util.ArrayList<>(shortProfile.facts());
        facts.set(2, new MainMenuProfile.Fact("world", Component.text("World"), Component.text("Long_世界_name_".repeat(12))));
        facts.set(3, new MainMenuProfile.Fact("location", Component.text("Location"), Component.text("-29999999, 319, 29999999")));
        var changed = new MainMenuProfile(AVATAR, shortProfile.name(), shortProfile.identity(), facts);
        for (var scale : FloatingMenuTextScale.values()) {
            var before = fixture(content(shortProfile.content(Component.text("Identity details"), (_, _) -> {}), null, null),
                    "heading", "destinations", "links-heading", "links", "quick", "footer");
            var after = fixture(content(changed.content(Component.text("Identity details"), (_, _) -> {}), null, null),
                    "heading", "destinations", "links-heading", "links", "quick", "footer");
            var first = SceneGeometryTestSupport.pose(before, scale.factor(), "heading");
            var second = SceneGeometryTestSupport.pose(after, scale.factor(), "heading");
            assertEquals(first.right(), second.right(), 1e-8, "heading @ " + scale);
            assertEquals(first.up(), second.up(), 1e-8, "heading @ " + scale);
            assertEquals(first.forward(), second.forward(), 1e-8, "heading @ " + scale);
            assertRotation(first.rotation(), second.rotation());
            assertNull(entry(before, "world").tooltip());
            assertFullTextAvailable(facts.get(2).value(),element(SceneGeometryTestSupport.measure(after,scale.factor()),"world"));
        }
    }

    @Test void addingChangingAndRemovingTheLatestAnnouncementPreservesTheLauncherAndNeighboringWidgets() throws Exception {
        var shortPreview = AnnouncementPreviewTestSupport.content(Language.ZH_CN, "服务器已完成维护。");
        var longPreview = AnnouncementPreviewTestSupport.content(Language.ZH_CN, "公告正文：欢迎参与社区建设。\n".repeat(40));
        var profile = profile().content(Component.text("Identity"), (_, _) -> {});
        var empty = fixture(content(profile, null, null), "heading", "destinations", "links-heading", "links", "quick", "footer")
                .toBuilder().viewport(MenuScene.DEFAULT_VIEWPORT).build();
        for (var scale : FloatingMenuTextScale.values()) {
            var before = SceneGeometryTestSupport.measure(empty, scale.factor());
            var pose = SceneGeometryTestSupport.pose(empty, scale.factor(), "heading");
            for (UiNode latest : new UiNode[]{shortPreview, longPreview, null}) {
                var scene = fixture(content(profile, null, null, latest),
                        "heading", "destinations", "links-heading", "links", "quick", "footer")
                        .toBuilder().viewport(MenuScene.DEFAULT_VIEWPORT).build();
                var frame = SceneGeometryTestSupport.measure(scene, scale.factor());
                var current = SceneGeometryTestSupport.pose(scene, scale.factor(), "heading");
                assertEquals(pose.right(), current.right(), 1e-8);
                assertEquals(pose.up(), current.up(), 1e-8);
                assertEquals(pose.forward(), current.forward(), 1e-8);
                assertRotation(pose.rotation(), current.rotation());
                for (String id : List.of("hub", "profile", "links")) {
                    var original = before.panels().stream().filter(panel -> panel.path().endsWith("/" + id)).findFirst().orElseThrow();
                    var actual = frame.panels().stream().filter(panel -> panel.path().endsWith("/" + id)).findFirst().orElseThrow();
                    assertEquals(original.size(), actual.size(), id + " @ " + scale);
                }
                assertDisjoint(frame);
            }
        }
    }

    @Test void profileWidthFollowsContentWithoutReservingEmptySpace() {
        var compact = new MainMenuProfile(AVATAR, Component.text("Alex"), Component.text("Member"), List.of(
                new MainMenuProfile.Fact("world", Component.text("World"), Component.text("world")),
                new MainMenuProfile.Fact("location", Component.text("Position"), Component.text("0, 64, 0"))));
        var expanded = new MainMenuProfile(AVATAR, compact.name(), compact.identity(), List.of(
                new MainMenuProfile.Fact("world", Component.text("World"), Component.text("world_with_a_long_name")),
                new MainMenuProfile.Fact("location", Component.text("Position"), Component.text("-29999999, 319, 29999999"))));
        var small = fixture(content(compact.content(Component.text("Identity details"), (_, _) -> {}), null, null),
                "heading", "destinations", "links-heading", "links", "quick", "footer");
        var large = fixture(content(expanded.content(Component.text("Identity details"), (_, _) -> {}), null, null),
                "heading", "destinations", "links-heading", "links", "quick", "footer");
        var smallFrame = SceneGeometryTestSupport.measure(small, 1);
        var largeFrame = SceneGeometryTestSupport.measure(large, 1);
        double compactWidth = smallFrame.panels().stream().filter(panel -> panel.path().endsWith("/profile"))
                .findFirst().orElseThrow().size().width();
        double expandedWidth = largeFrame.panels().stream().filter(panel -> panel.path().endsWith("/profile"))
                .findFirst().orElseThrow().size().width();
        assertTrue(compactWidth < expandedWidth);
        assertTrue(expandedWidth <= MainMenuSidebar.MAX_WIDTH + 1e-6);
        assertDisjoint(smallFrame);
        assertDisjoint(largeFrame);
        SceneGeometryTestSupport.assertVisualContentsFit(small, 1);
        SceneGeometryTestSupport.assertVisualContentsFit(large, 1);
    }

    @Test void shortFactLabelsStayOnOneLineBesideLongValues() {
        var profile = new MainMenuProfile(AVATAR, Component.text("Alex"), Component.text("成员"), List.of(
                new MainMenuProfile.Fact("joined", Component.text("首次加入"), Component.text("2020-01-01")),
                new MainMenuProfile.Fact("world", Component.text("世界"), Component.text("world_with_a_long_name")),
                new MainMenuProfile.Fact("location", Component.text("坐标"), Component.text("-29999999, 319, 29999999"))));
        var scene = fixture(content(profile.content(Component.text("身份详情"), (_, _) -> {}), null, null),
                "heading", "destinations", "links-heading", "links", "quick", "footer");
        var frame = SceneGeometryTestSupport.measure(scene, 1);
        for (var fact : profile.facts()) {
            var label = element(frame, fact.key() + ":label").node();
            String text = PlainTextComponentSerializer.plainText().serialize(fact.label());
            assertTrue(UiFontMetrics.width(text) <= label.textWidthPixels(), fact.key());
        }
        assertDisjoint(frame);
        SceneGeometryTestSupport.assertVisualContentsFit(scene, 1);
    }

    private static PlotNoticeBoard notice(String body) {
        return new PlotNoticeBoard(UUID.randomUUID(), "Long plot name 庭院与社区建筑", UUID.randomUUID(),
                "Long_Player_Name", body, "Parent plot");
    }

    @Test void governanceWidgetPagesPreserveTheLauncherAndKeepDetailsAvailable() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            BiFunction<Message,Object[],Component> text = (message,args) -> Component.text(FluentTestSupport.text(bundle,message,args));
            for (var scale : FloatingMenuTextScale.values()) for (boolean notice : new boolean[]{false,true}) {
                FloatingMenuPose heading = null;
                for (int count : new int[]{0,1,2,3}) {
                    var activity = org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceActivityTestSupport.content(language,count);
                    var profile = profile(text).content(Component.text("Identity"),(_,_) -> {});
                    var plot = notice ? PlotNoticeTestSupport.preview(notice("社区花园公告。\n".repeat(20)),
                            text.apply(Message.PLOT_NOTICE_READ,new Object[0]),(_,_) -> {}) : null;
                    boolean seen = false; boolean seenPlot = false;
                    for (var page : widgetPages(launcher(text,false,false),profile,plot,activity,
                            AnnouncementPreviewTestSupport.content(language,"最近公告\n".repeat(20)),scale.factor())) {
                        var scene = page.scene(); var frame = page.frame();
                        assertDisjoint(frame); SceneGeometryTestSupport.assertVisualContentsFit(scene,scale.factor());
                        var current = SceneGeometryTestSupport.pose(scene,scale.factor(),"heading");
                        if (heading == null) heading = current; else assertEquals(heading,current,language+" @ "+scale+" / "+count);
                        if (visible(frame,"notice-body")) seenPlot = true;
                        if (!visible(frame,"activity:vote:1")) continue;
                        seen = true;
                        var card = frame.panels().stream().filter(panel -> panel.path().endsWith("/governance")).findFirst().orElseThrow();
                        assertTrue(card.size().width() <= 300+1e-6);
                        assertTrue(element(frame,"activity:vote:1").path().startsWith(card.path()+"/"));
                        assertTrue(SceneGeometryTestSupport.pose(scene,scale.factor(),"activity:vote:1").yawDegrees() > 0);
                        assertEquals(0,SceneGeometryTestSupport.pose(scene,scale.factor(),"activity:vote:1").pitchDegrees(),1e-8);
                        var companion = org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceActivityTestSupport.withDetails(scene,language,count);
                        for (int fov : new int[]{30,70,110}) {
                            var popup = SceneGeometryTestSupport.popoverFrame(companion,scale.factor(),new FloatingMenuFieldOfView(fov));
                            assertNotNull(popup); assertDisjoint(popup);
                        }
                    }
                    assertEquals(count>0,seen); assertEquals(notice,seenPlot);
                }
            }
        }
    }

    private record WidgetPage(MenuScene scene,UiFrame frame) { }

    private static java.util.List<WidgetPage> widgetPages(MenuScene.Builder builder,UiNode profile,UiNode plot,
                                                          UiNode activity,UiNode announcement,double textScale) {
        var result = new java.util.ArrayList<WidgetPage>();
        int leftCount = 1, rightCount = 1;
        for (int left = 0; left < leftCount; left++) for (int right = 0; right < rightCount; right++) {
            var scene = builder.content(content(profile,plot,activity,announcement,left,right)).build();
            var frame = SceneGeometryTestSupport.measure(scene,textScale);
            for (var pagination : frame.pagination()) {
                if (pagination.path().equals("root/left/content")) leftCount = pagination.page().count();
                if (pagination.path().equals("root/right/content")) rightCount = pagination.page().count();
            }
            result.add(new WidgetPage(scene,frame));
        }
        return result;
    }

    private static boolean visible(UiFrame frame,String key) {
        return frame.elements().stream().anyMatch(element -> element.node().key().value().equals(key));
    }

    private static MenuScene.Builder launcher(BiFunction<Message,Object[],Component> text,boolean pvp,boolean afk) {
        var builder = MenuScene.screen("main").viewport(MenuScene.DEFAULT_VIEWPORT);
        builder.information("heading",text.apply(Message.MAIN_MENU_TITLE,new Object[0])).region("heading");
        Message[] destinations = {Message.MAIN_HOME,Message.PLOT_TITLE,Message.GOVERNANCE_MENU_TITLE,Message.MAIN_MUSIC,Message.MAIN_ANNOUNCEMENTS};
        for (int index = 0; index < destinations.length; index++) builder.item("destination:"+index,SceneGeometryTestSupport.item(),text.apply(destinations[index],new Object[0])).region("destinations");
        builder.information("links-heading",text.apply(Message.MAIN_LINKS,new Object[0])).region("links-heading");
        for (var link : List.of(Message.MAIN_WEBSITE,Message.MAIN_MAP,Message.MAIN_WIKI)) builder.control(link.name(),text.apply(link,new Object[0])).region("links");
        builder.control("afk",text.apply(Message.MAIN_AFK_STATUS,new Object[]{afk ? "afk" : "online"})).selected(afk).region("quick");
        builder.control("pvp-toggle",text.apply(Message.MAIN_PVP_STATUS,new Object[]{pvp ? "on" : "off"})).selected(pvp).region("quick").primary((_,_) -> {});
        builder.navigation("settings",text.apply(Message.MAIN_SETTINGS,new Object[0])).region("footer");
        builder.close(text.apply(Message.CLOSE,new Object[0])).region("footer");
        return builder;
    }

    private static UiNode content(UiNode profile, UiNode plot, UiNode activity) {
        return content(profile, plot, activity, null);
    }

    private static UiNode content(UiNode profile, UiNode plot, UiNode activity, UiNode announcement) {
        return content(profile,plot,activity,announcement,0,0);
    }

    private static UiNode content(UiNode profile,UiNode plot,UiNode activity,UiNode announcement,int leftPage,int rightPage) {
        var left = new java.util.ArrayList<UiSurface>();
        left.add(UiTemplates.sidebar("profile", profile));
        if (announcement != null) left.add(UiTemplates.sidebar("announcements", announcement));
        var right = new java.util.ArrayList<UiSurface>();
        right.add(UiTemplates.sidebar("links", UiTemplates.menu(UiTemplates.heading("links-heading"), UiTemplates.list("links"))));
        if (activity != null) right.add(UiTemplates.sidebar("governance", activity));
        if (plot != null) right.add(UiTemplates.sidebar("plot", plot));
        return MainMenuComposition.content(MainMenuSidebar.column("left", leftPage, _ -> {}, left), MainMenuSidebar.column("right", rightPage, _ -> {}, right), 0, _ -> {});
    }

    private static MenuScene noticeScene(String body, FloatingMenuCommand open) {
        return fixture(content(profile().content(Component.text("Identity"), (_,_) -> {}),
                PlotNoticeTestSupport.preview(notice(body), Component.text("查看全文"), open), null),
                "heading", "destinations", "links-heading", "links", "quick", "footer")
                .toBuilder().viewport(MenuScene.DEFAULT_VIEWPORT).build();
    }

    private static MainMenuProfile profile() { return profile((message, _) -> Component.text(message.key())); }

    private static MainMenuProfile profile(BiFunction<Message, Object[], Component> text) {
        String[] keys = {"play-time", "first-joined", "world", "location", "client-version", "ping"};
        Message[] labels = {Message.STAT_PLAY_TIME, Message.STAT_FIRST_JOINED, Message.STAT_WORLD,
                Message.STAT_LOCATION, Message.STAT_CLIENT_VERSION, Message.STAT_PING};
        Component[] values = {text.apply(Message.TIME_HOURS_MINUTES, new Object[]{1234, 56}), Component.text("2020-01-01"),
                Component.text("world_with_a_long_name"), Component.text("-29999999, 64, 29999999"), Component.text("⚠ 26.3"),
                text.apply(Message.STAT_PING_VALUE, new Object[]{123})};
        var facts = new java.util.ArrayList<MainMenuProfile.Fact>();
        for (int i = 0; i < keys.length; i++) facts.add(new MainMenuProfile.Fact(keys[i], text.apply(labels[i], new Object[0]), values[i]));
        return new MainMenuProfile(AVATAR, Component.text("Long_Player_Name"), text.apply(Message.STAT_ROLE, new Object[0])
                .append(Component.text(": ")).append(text.apply(Message.ROLE_CUSTODIAN, new Object[0])), facts);
    }

    private static void assertDestinationReadingOrder(UiFrame frame, String detail) {
        var buttons = frame.elements().stream().filter(element -> element.node().key().value().startsWith("destination:")).toList();
        assertFalse(buttons.isEmpty(), detail);
        for (int index = 1; index < buttons.size(); index++) {
            var previous = buttons.get(index - 1);
            var current = buttons.get(index);
            double previousCenter = previous.y() + previous.size().height() / 2;
            double currentCenter = current.y() + current.size().height() / 2;
            if (Math.abs(previousCenter - currentCenter) < 1e-6)
                assertTrue(current.x() > previous.x(), detail + ": read each row in order");
            else assertTrue(currentCenter > previousCenter, detail + ": continue on the next row");
        }
    }
}
