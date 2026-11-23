package org.encinet.mik.shared.menu.runtime.window;

import org.encinet.mik.shared.menu.runtime.input.MenuWindowInput;
import org.encinet.mik.shared.menu.runtime.layout.SceneGeometry;

import java.util.*;
import java.util.concurrent.atomic.*;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuWorkspaceSessionTest {
    private static final Component TEXT = Component.text("Window");
    private static final MenuWorkspaceSession.Labels LABELS = new MenuWorkspaceSession.Labels(TEXT,TEXT,TEXT,TEXT,TEXT,TEXT);
    private static MenuScene scene(String name) {
        return MenuScene.screen(name,Component.text(name)).content(Ui.button(TEXT).key("shared-field")
                .primary((_,_) -> {}).build()).build();
    }
    private static final class Host implements MenuWorkspaceSession.Host {
        final UUID root = UUID.randomUUID();
        final FloatingMenuHandle rootHandle = mock(FloatingMenuHandle.class);
        final MenuWorkspaceSession workspace;
        final Map<UUID,Integer> closes = new HashMap<>();
        final List<RuntimeException> errors = new ArrayList<>();
        MenuScene rootScene = scene("root");
        SceneGeometry frame;
        int focusChanges;
        boolean rootClosed;
        Host() {
            when(rootHandle.id()).thenReturn(root);
            when(rootHandle.state()).thenReturn(FloatingMenuState.ACTIVE);
            workspace = new MenuWorkspaceSession(root,this); changed();
        }
        public FloatingMenuHandle root() { return rootHandle; }
        public void requireThread(String operation) { }
        public void execute(Runnable action) { action.run(); }
        public void changed() { frame = SceneGeometry.measure(workspace.compose(rootScene,LABELS),1,FloatingMenuFieldOfView.DEFAULT,1); }
        public void focusChanged() { focusChanges++; }
        public boolean cancelInput(UUID owner) { return false; }
        public void lifecycle(MenuScene scene, FloatingMenuHandle handle, FloatingMenuState before, FloatingMenuState after, FloatingMenuCloseReason reason) {
            if (after == FloatingMenuState.CLOSED) closes.merge(handle.id(),1,Integer::sum);
        }
        public void rootClosed(FloatingMenuCloseReason reason) { rootClosed = true; }
        public void failure(RuntimeException error) { errors.add(error); }
        public MenuInputLease suspend(UUID window) { return workspace.suspend(window); }
        public void popover(UUID owner, UiKey source, java.util.function.Function<Runnable,MenuPanel> content) { }
        public void popover(UUID owner, MenuPopover content) { }
        public boolean closePopover(UUID owner, MenuPopover expected) { return false; }
        MenuWindowHandle<Void> tool(String key) {
            return workspace.open(root,MenuWindowSpec.tool(UiKey.named(key)),Void.class,_ -> () -> scene(key),_ -> {});
        }
    }

    @Test void siblingsKeepTheirIdentityAndModalScopeDoesNotBlockOtherBranches() {
        var host = new Host();
        var a = host.tool("a"); var b = host.tool("b");
        var confirm = a.openWindow(MenuWindowSpec.task(UiKey.named("confirm")),_ -> () -> scene("confirm"));
        assertFalse(host.workspace.allowed(a.id()));
        assertTrue(host.workspace.allowed(b.id()));
        assertTrue(host.workspace.allowed(confirm.id()));
        b.activate(); assertTrue(b.focused());
        a.activate(); assertTrue(confirm.focused());
        confirm.close();
        assertTrue(a.focused());
        assertTrue(host.workspace.allowed(a.id()));
        assertEquals(FloatingMenuState.ACTIVE,b.state());
    }

    @Test void workspaceManagementRetainsAuthoredPlanesWithoutAnEnclosingSurfaceOrInferredTitle() {
        var host = new Host();
        host.rootScene = MenuScene.screen("launcher",Component.text("Main menu")).withoutTitle()
                .content(UiTemplates.horizontalPanels(UiLength.ui(12),
                        UiTemplates.sidebar("announcements",Ui.heading(Component.text("Recent announcements")).key("notice-heading").build()),
                        UiTemplates.window("launcher",Ui.button(Component.text("Close")).key("native-close")
                                .primary((_,handle) -> handle.close()).build()))).build();
        host.changed();
        assertEquals(MenuWorkspaceSession.MAIN.value(),host.workspace.sourceScope(host.root));
        assertTrue(host.workspace.movable(host.root));
        assertEquals(2,host.frame.frame().surfaces().size(),"Management must not add an enclosing plane");
        assertEquals(1,host.frame.frame().elements().stream().filter(node -> node.node().key().value().equals("notice-heading")).count());
        assertFalse(host.frame.frame().elements().stream().anyMatch(node -> node.node().key().value().equals("title")),
                "The body heading and window-list label must never become a workspace title bar");
        assertTrue(host.frame.frame().elements().stream().anyMatch(node -> node.node().key().value().equals("move")));
        assertFalse(host.frame.frame().elements().stream().anyMatch(node -> node.node().key().value().equals("close")),
                "Workspace dismissal stays in its authored controls");
        var tool = host.tool("editor");
        assertEquals(4,host.frame.frame().surfaces().size(),"Only the companion and its tray add planes");
        assertFalse(host.frame.frame().elements().stream().anyMatch(node -> node.node().key().value().equals("restore-"+host.root)),
                "The visible workspace needs no tray card");
        tool.minimize(); assertEquals(FloatingMenuState.SUSPENDED,tool.state());
        assertTrue(host.frame.frame().elements().stream().anyMatch(node -> node.node().key().value().equals("restore-"+tool.id())));
        tool.restore(); assertTrue(tool.focused());
        tool.close();
        assertEquals(2,host.frame.frame().surfaces().size());
        var pose = UiTransform.IDENTITY.translated(40,20,0).rotated(20,-10,0);
        host.workspace.transform(host.root,pose,true);
        assertEquals(pose,host.workspace.transform(host.root));
        assertEquals(2,host.frame.frame().surfaces().size(),"Moving a workspace must not manufacture a plane");
    }

    @Test void repeatedOpenActivatesSameInstanceWithoutReconstructingDrafts() {
        var host = new Host(); var constructed = new AtomicInteger();
        var spec = MenuWindowSpec.tool(UiKey.named("object"));
        var first = host.workspace.open(host.root,spec,Void.class,_ -> { constructed.incrementAndGet(); return () -> scene("one"); },_ -> {});
        host.tool("other");
        var same = host.workspace.open(host.root,spec,Void.class,_ -> { fail("must retain content"); return null; },_ -> {});
        assertEquals(first.id(),same.id()); assertTrue(first.focused()); assertEquals(1,constructed.get());
    }

    @Test void backgroundCloseLeavesCurrentFocusAndStaleHandlesCannotCloseReopenedKeys() {
        var host = new Host(); var first = host.tool("a"); var other = host.tool("b");
        var oldPage = first.current(); int focused = host.focusChanges;
        first.close(); assertTrue(other.focused()); assertEquals(focused,host.focusChanges);
        var next = host.tool("a");
        assertNotEquals(first.id(),next.id()); assertFalse(oldPage.getAsBoolean());
        first.close(); first.refresh(); first.activate();
        assertEquals(FloatingMenuState.ACTIVE,next.state()); assertTrue(next.focused());
    }

    @Test void independentMeasurementRetainsRootNodeSizeAndNamespacesIdenticalFieldKeys() {
        var host = new Host();
        var original = host.frame.frame().elements().stream().filter(node -> node.node().key().value().equals("shared-field")).findFirst().orElseThrow().size();
        var a = host.tool("a"); var b = host.tool("b");
        var fields = host.frame.frame().elements().stream().filter(node -> node.node().key().value().equals("shared-field")).toList();
        assertEquals(3,fields.size());
        assertEquals(Set.of(host.root,a.id(),b.id()),fields.stream().map(node -> host.workspace.ownerOf(node.path())).collect(java.util.stream.Collectors.toSet()));
        assertEquals(original,fields.stream().filter(node -> host.workspace.ownerOf(node.path()).equals(host.root)).findFirst().orElseThrow().size());
        var depths = fields.stream().map(node -> host.frame.poses().get(node.path()).forward()).sorted().toList();
        assertTrue(depths.get(0) < depths.get(1)); assertTrue(depths.get(1) < depths.get(2));
    }

    @Test void independentWindowsUseOpaqueBackplatesInEveryTheme() {
        var host = new Host();
        host.rootScene = MenuScene.screen("library").content(UiTemplates.window("library",Ui.text(TEXT).build())).build();
        var tool = host.tool("device");
        tool.openWindow(MenuWindowSpec.task(UiKey.named("edit")),_ -> () -> scene("edit"));
        var themes = new ArrayList<UiThemeColors>(List.of(UiPalette.values()));
        themes.add(UiCustomTheme.copyOf(UiPalette.WARM).withColor(UiColorRole.WINDOW,0x123456,.12));
        for (var theme : themes) {
            var geometry = SceneGeometry.measure(host.workspace.compose(host.rootScene,LABELS),1,FloatingMenuFieldOfView.DEFAULT,1,theme);
            var backplates = geometry.frame().surfaces().stream()
                    .filter(surface -> !host.workspace.ownerOf(surface.path()).equals(host.root)).toList();
            assertEquals(2,backplates.size());
            for (var surface : backplates) {
                assertEquals(255,surface.style().backgroundArgb(theme) >>> 24);
                assertEquals(theme.rgb(UiColorRole.WINDOW),surface.style().backgroundArgb(theme) & 0xffffff);
            }
            assertTrue(geometry.generatedDecorations().values().stream().map(FloatingMenuDecoration::content)
                    .filter(FloatingMenuDecoration.Surface.class::isInstance).map(FloatingMenuDecoration.Surface.class::cast)
                    .anyMatch(surface -> surface.background() == (0xff000000 | theme.rgb(UiColorRole.WINDOW))));
            var root = geometry.frame().surfaces().stream().filter(surface -> host.workspace.ownerOf(surface.path()).equals(host.root))
                    .findFirst().orElseThrow();
            assertEquals(theme.argb(UiColorRole.WINDOW),root.style().backgroundArgb(theme));
        }
    }

    @Test void closePlanWaitsForAllDecisionsAndCancellationKeepsOwnedWindows() {
        var host = new Host(); var decision = new AtomicReference<MenuCloseRequest>(); var closed = new AtomicInteger();
        var parent = host.workspace.open(host.root,MenuWindowSpec.tool(UiKey.named("edit")).onCloseRequested(decision::set),Void.class,
                _ -> new MenuPanel() { public MenuScene render() { return scene("editor"); } public void close() { closed.incrementAndGet(); } },_ -> {});
        var child = parent.openWindow(MenuWindowSpec.task(UiKey.named("child")),_ -> () -> scene("child"));
        parent.close(); assertTrue(decision.get().active()); assertEquals(0,closed.get());
        decision.get().cancel(); assertEquals(FloatingMenuState.ACTIVE,child.state());
        parent.close(); decision.get().approve();
        assertEquals(FloatingMenuState.CLOSED,child.state()); assertEquals(FloatingMenuState.CLOSED,parent.state());
        assertEquals(1,closed.get()); decision.get().approve(); assertEquals(1,closed.get());
    }

    @Test void typedResultsAreDeliveredOnceAndForcedParentCloseDoesNotCallDeadOwner() {
        var host = new Host(); var results = new ArrayList<MenuWindowResult<String>>();
        var parent = host.tool("parent");
        var child = parent.openWindow(MenuWindowSpec.task(UiKey.named("choose")),String.class,_ -> () -> scene("choose"),results::add);
        child.complete("picked"); child.complete("twice");
        assertEquals(1,results.size()); assertEquals("picked",results.getFirst().value());
        parent.openWindow(MenuWindowSpec.task(UiKey.named("choose")),String.class,_ -> () -> scene("choose"),results::add);
        host.workspace.close(FloatingMenuCloseReason.QUIT);
        assertEquals(1,results.size()); assertEquals(FloatingMenuState.CLOSED,parent.state());
    }

    @Test void aCloseReviewCanOpenAboveRetainedModalChildrenAndCancellingRestoresTheOriginalTree() {
        var host = new Host(); var decision = new AtomicReference<MenuCloseRequest>();
        var parent = host.workspace.open(host.root,MenuWindowSpec.tool(UiKey.named("draft")).onCloseRequested(decision::set),
                Void.class,_ -> () -> scene("draft"),_ -> {});
        var editor = parent.openWindow(MenuWindowSpec.task(UiKey.named("editor")),_ -> () -> scene("editor"));
        parent.close();
        var review = parent.openWindow(MenuWindowSpec.task(UiKey.named("review")),_ -> () -> scene("review"));
        assertTrue(host.workspace.allowed(review.id())); assertFalse(host.workspace.allowed(editor.id()));
        review.close();
        assertFalse(decision.get().active()); assertEquals(FloatingMenuState.CLOSED,review.state());
        assertEquals(FloatingMenuState.ACTIVE,editor.state()); assertTrue(host.workspace.allowed(editor.id()));
    }

    @Test void localPageNavigationInvalidatesOldScopeAndPreservesItsOwner() {
        var host = new Host(); var tool = host.tool("tool"); var first = tool.current();
        tool.openChild(scene("next")); assertFalse(first.getAsBoolean()); assertEquals(1,tool.depth());
        tool.back(); assertTrue(first.getAsBoolean()); assertEquals(0,tool.depth());
        assertEquals(FloatingMenuState.ACTIVE,host.rootHandle.state());
    }

    @Test void minimizeReleasesPresentationWithoutEndingContentAndModalChildrenPreventIt() {
        var host = new Host(); var tool = host.tool("tool"); var child = tool.openWindow(MenuWindowSpec.task(UiKey.named("edit")),_ -> () -> scene("edit"));
        tool.minimize(); assertEquals(FloatingMenuState.ACTIVE,tool.state());
        child.close(); tool.minimize(); assertEquals(FloatingMenuState.SUSPENDED,tool.state());
        assertFalse(host.frame.frame().elements().stream().anyMatch(node -> host.workspace.ownerOf(node.path()).equals(tool.id())));
        tool.restore(); assertEquals(FloatingMenuState.ACTIVE,tool.state()); assertTrue(tool.focused());
    }

    @Test void rootMinimizationPreservesPagesTransformsAndDraftInstancesUntilTrayRestore() {
        var host = new Host(); var constructed = new AtomicInteger(); var disposed = new AtomicInteger();
        var tool = host.workspace.open(host.root,MenuWindowSpec.tool(UiKey.named("draft")),Void.class,_ -> {
            constructed.incrementAndGet();
            return new MenuPanel() { public MenuScene render() { return scene("draft"); } public void close() { disposed.incrementAndGet(); } };
        },_ -> {});
        tool.openChild(scene("second-page"));
        var token = tool.current();
        var pose = UiTransform.IDENTITY.translated(80,15,0).rotated(25,-10,0);
        host.workspace.transform(tool.id(),pose,true);
        host.workspace.minimize(host.root);
        assertTrue(host.workspace.minimized()); assertEquals(FloatingMenuState.SUSPENDED,tool.state());
        assertEquals(0,disposed.get()); assertTrue(token.getAsBoolean());
        assertFalse(host.frame.frame().elements().stream().anyMatch(node -> node.node().key().value().equals("shared-field")));
        var restore = host.frame.frame().elements().stream().filter(node -> node.node().key().value().equals("restore-"+host.root)).findFirst().orElseThrow();
        assertTrue(host.frame.poses().get(restore.path()).up() < 0,"Minimized cards stay below the central reading direction");
        restore.node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,host.rootHandle,FloatingMenuInteraction.PRIMARY);
        assertFalse(host.workspace.minimized()); assertTrue(tool.focused());
        assertEquals(1,tool.depth()); assertEquals(pose,host.workspace.transform(tool.id()));
        assertEquals(1,constructed.get()); assertEquals(0,disposed.get());
    }

    @Test void trayCardsUseTheirTitlesWithoutReadOnlyPictograms() {
        var host = new Host(); var tool = host.tool("tool");
        assertTrue(host.frame.frame().elements().stream().noneMatch(node -> node.node().key().value().equals("expand")),
                "Root and child windows expose no expansion control");
        var active = host.frame.frame().elements().stream().filter(node -> node.path().contains("/window-switcher/")).toList();
        assertEquals(1,active.size());
        assertTrue(active.stream().allMatch(node -> node.node().content() instanceof UiContent.Text));
        assertTrue(active.stream().allMatch(node -> node.node().input().tooltip().isEmpty()));
        assertTrue(active.stream().filter(node -> node.node().key().value().equals("restore-"+tool.id()))
                .findFirst().orElseThrow().node().input().selected());
        tool.minimize();
        var minimized = host.frame.frame().elements().stream().filter(node -> node.path().contains("/card-"+tool.id()+"/")).toList();
        assertEquals(2,minimized.size(),"A minimized card keeps its restore title and close action");
        assertTrue(minimized.stream().allMatch(node -> node.node().input().interactive()));
        assertTrue(minimized.stream().anyMatch(node -> node.node().key().value().equals("restore-"+tool.id())));
        assertTrue(minimized.stream().anyMatch(node -> node.node().key().value().equals("close-"+tool.id())));
    }

    @Test void rootAndToolPoliciesGuardBothChromeAndProgrammaticOperations() {
        var host = new Host();
        host.rootScene = scene("root").toBuilder().windowPolicy(MenuWindowPolicy.FIXED).build(); host.changed();
        host.workspace.minimize(host.root); host.workspace.move(host.root);
        assertFalse(host.workspace.minimized()); assertFalse(host.workspace.movable(host.root));
        for (String key : List.of("minimize","move","expand")) assertFalse(host.frame.frame().elements().stream()
                .anyMatch(node -> node.node().key().value().equals(key)));
        assertFalse(host.frame.frame().elements().stream().anyMatch(node -> node.node().key().value().equals("close")));
        var tool = host.workspace.open(host.root,MenuWindowSpec.tool(UiKey.named("fixed")).policy(MenuWindowPolicy.FIXED),Void.class,_ -> () -> scene("fixed"),_ -> {});
        tool.minimize(); tool.move();
        assertEquals(FloatingMenuState.ACTIVE,tool.state()); assertFalse(host.workspace.movable(tool.id()));
        assertThrows(IllegalArgumentException.class,() -> MenuWindowSpec.task(UiKey.named("invalid")).policy(MenuWindowPolicy.STANDARD));
    }

    @Test void modalDescendantsPreventRootMinimizationAndRootCloseCanBeCancelled() {
        var host = new Host(); var tool = host.tool("tool");
        var task = tool.openWindow(MenuWindowSpec.task(UiKey.named("confirmation")),_ -> () -> scene("confirm"));
        host.workspace.minimize(host.root); assertFalse(host.workspace.minimized()); assertTrue(task.focused());
        task.close();
        host.rootScene = scene("root").toBuilder().windowPolicy(MenuWindowPolicy.STANDARD.onCloseRequested(MenuCloseRequest::cancel)).build(); host.changed();
        host.workspace.requestClose(host.root,FloatingMenuCloseReason.USER);
        assertFalse(host.rootClosed); assertEquals(FloatingMenuState.ACTIVE,tool.state());
    }

    @Test void suspensionResumesOnlyTheOriginalScopeAndNoLateLeaseReopensAClosedWorkspace() {
        var host = new Host(); var tool = host.tool("tool"); var lease = tool.suspend();
        assertTrue(lease.active()); assertTrue(host.workspace.paused()); assertFalse(host.workspace.allowed(tool.id()));
        assertTrue(lease.complete()); assertFalse(host.workspace.paused());
        var old = tool.suspend(); host.workspace.close(FloatingMenuCloseReason.WORLD_CHANGE);
        assertFalse(old.active()); assertFalse(old.complete()); assertEquals(FloatingMenuState.CLOSED,tool.state());
    }

    @Test void aFailedOpenRetainsThePublishedParentAndCleansNewContent() {
        var host = new Host(); var parent = host.tool("parent"); var closed = new AtomicInteger();
        assertThrows(UiOverflowException.class,() -> parent.openWindow(MenuWindowSpec.task(UiKey.named("oversized")),_ -> new MenuPanel() {
            public MenuScene render() { return MenuScene.screen("bad").content(Ui.text(TEXT).sizing(UiSizing.fixed(new UiSize(5000,5000))).build()).build(); }
            public void close() { closed.incrementAndGet(); }
        }));
        assertEquals(1,closed.get()); assertTrue(parent.focused()); assertEquals(FloatingMenuState.ACTIVE,parent.state());
    }
    @Test void readingBudgetRetainsHiddenDraftsAndCapacityFailureKeepsEveryInstance() {
        var host = new Host(); var handles = new ArrayList<MenuWindowHandle<Void>>();
        for (int index = 0; index < MenuWorkspaceSession.MAX_WINDOWS - 1; index++) handles.add(host.tool("tool-" + index));
        var visible = host.frame.frame().elements().stream().filter(node -> node.node().key().value().equals("shared-field")).toList();
        assertEquals(MenuWorkspaceSession.MAX_READING_WINDOWS,visible.size());
        assertThrows(UiOverflowException.class,() -> host.tool("overflow"));
        assertTrue(handles.getLast().focused());
        handles.getFirst().activate();
        assertTrue(host.frame.frame().elements().stream().anyMatch(node -> host.workspace.ownerOf(node.path()).equals(handles.getFirst().id())));
        assertTrue(handles.stream().allMatch(handle -> handle.state() == FloatingMenuState.ACTIVE));
        host.workspace.requestClose(host.root,FloatingMenuCloseReason.USER);
        assertTrue(host.rootClosed);
        assertTrue(handles.stream().allMatch(handle -> handle.state() == FloatingMenuState.CLOSED));
    }

    @Test void pageLifetimeAndWorkspaceModalityHaveSeparateScopes() {
        var host = new Host(); var parent = host.tool("parent"); var sibling = host.tool("sibling");
        var page = parent.openWindow(MenuWindowSpec.tool(UiKey.named("page-child")).lifetime(MenuWindowSpec.Lifetime.PAGE),_ -> () -> scene("page-child"));
        var owned = parent.openWindow(MenuWindowSpec.tool(UiKey.named("owner-child")),_ -> () -> scene("owner-child"));
        parent.openChild(scene("next"));
        assertEquals(FloatingMenuState.CLOSED,page.state());
        assertEquals(FloatingMenuState.ACTIVE,owned.state());
        var modal = owned.openWindow(MenuWindowSpec.task(UiKey.named("global")).modality(MenuWindowSpec.Modality.WORKSPACE),_ -> () -> scene("global"));
        assertFalse(host.workspace.allowed(sibling.id())); assertFalse(host.workspace.allowed(host.root));
        assertTrue(host.workspace.allowed(modal.id())); modal.close();
        assertTrue(host.workspace.allowed(sibling.id()));
    }

    @Test void failedPageNavigationKeepsTheOriginalScopeAndOwnedChildren() {
        var host = new Host(); var parent = host.tool("parent"); var scope = parent.current();
        var child = parent.openWindow(MenuWindowSpec.tool(UiKey.named("child")).lifetime(MenuWindowSpec.Lifetime.PAGE),_ -> () -> scene("child"));
        var oversized = MenuScene.screen("bad").content(Ui.text(TEXT).sizing(UiSizing.fixed(new UiSize(5000,5000))).build()).build();
        assertThrows(UiOverflowException.class,() -> parent.openChild(oversized));
        assertTrue(scope.getAsBoolean()); assertEquals(0,parent.depth());
        assertEquals(FloatingMenuState.ACTIVE,child.state());
    }

    @Test void cleanupCannotReenterAClosedOwnerAndFailuresDoNotPreventSiblingCleanup() {
        var host = new Host(); var parent = host.tool("parent"); var closed = new AtomicInteger();
        parent.openWindow(MenuWindowSpec.tool(UiKey.named("cleanup")),_ -> new MenuPanel() {
            public MenuScene render() { return scene("cleanup"); }
            public void close() {
                closed.incrementAndGet();
                assertThrows(IllegalStateException.class,() -> parent.openWindow(MenuWindowSpec.tool(UiKey.named("orphan")),_ -> () -> scene("orphan")));
                throw new IllegalStateException("cleanup failed");
            }
        });
        var sibling = host.tool("sibling");
        assertThrows(RuntimeException.class,() -> host.workspace.close(FloatingMenuCloseReason.QUIT));
        assertEquals(1,closed.get()); assertEquals(FloatingMenuState.CLOSED,sibling.state());
        assertTrue(host.workspace.empty()); host.workspace.close(); assertEquals(1,closed.get());
    }

    @Test void publishedForegroundPlaneBlocksBackgroundEvenWhereItHasNoEnabledControl() {
        var host = new Host(); var back = host.tool("back");
        var front = host.workspace.open(host.root,MenuWindowSpec.tool(UiKey.named("front")),Void.class,
                _ -> () -> MenuScene.screen("front",TEXT).content(Ui.surface(Ui.column()
                        .child(Ui.button(TEXT).key("disabled").disabled(TEXT).build())
                        .child(Ui.text(Component.empty()).key("blank").sizing(UiSizing.fixed(new UiSize(180,100))).build()).build())
                        .key("transparent").style(UiStyle.PLAIN).build()).build(),_ -> {});
        java.util.function.Function<FloatingMenuPose,org.bukkit.util.Vector> position = pose ->
                new org.bukkit.util.Vector(pose.right(),pose.up(),20 - pose.forward());
        var surface = host.frame.panels().stream().filter(panel -> host.workspace.ownerOf(panel.path()).equals(front.id()))
                .filter(panel -> panel.path().endsWith("/transparent")).findFirst().orElseThrow();
        var eye = new org.bukkit.util.Vector();
        var aim = position.apply(surface.pose());
        var selected = MenuWindowInput.frontmost(host.frame.panels(),eye,aim.clone().subtract(eye),position,UiRotation.IDENTITY,1);
        assertEquals(front.id(),host.workspace.ownerOf(selected));
        assertNotEquals(back.id(),host.workspace.ownerOf(selected));
        assertNull(MenuWindowInput.frontmost(host.frame.panels(),eye,new org.bukkit.util.Vector(1,0,0),position,UiRotation.IDENTITY,1));
    }

    @Test void nonmodalTasksRemainReachableThroughTheWindowListWhenTheyLeaveTheReadingBudget() {
        var host = new Host(); var parent = host.tool("parent");
        var task = parent.openWindow(MenuWindowSpec.task(UiKey.named("task")).modality(MenuWindowSpec.Modality.NONE),_ -> () -> scene("task"));
        host.tool("other"); host.tool("latest");
        assertFalse(host.frame.frame().elements().stream().anyMatch(node -> host.workspace.ownerOf(node.path()).equals(task.id())));
        var activate = host.frame.frame().elements().stream().filter(node -> node.node().key().value().equals("restore-" + task.id())).findFirst().orElseThrow();
        activate.node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,host.rootHandle,FloatingMenuInteraction.PRIMARY);
        assertTrue(task.focused());
    }

    @Test void failingContentCleanupStillClosesTheRootHostAndRevokesTheWholeWorkspace() {
        var host = new Host();
        host.workspace.open(host.root,MenuWindowSpec.tool(UiKey.named("failure")),Void.class,_ -> new MenuPanel() {
            public MenuScene render() { return scene("failure"); }
            public void close() { throw new IllegalStateException("resource failed"); }
        },_ -> {});
        host.workspace.requestClose(host.root,FloatingMenuCloseReason.USER);
        assertTrue(host.rootClosed); assertTrue(host.workspace.empty());
        assertFalse(host.workspace.allowed(host.root)); assertEquals(1,host.errors.size());
    }

}
