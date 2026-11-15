package org.encinet.mik.feature.entertainment.music.ui;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MusicTaskWindowTest {
    @Test void returningRestoresTheParentPositionAndRetiresBothPreviousPagesActions() {
        var parent = mock(MenuPanel.class);
        var child = mock(MenuPanel.class);
        var position = new AtomicInteger(4);
        var parentAction = new AtomicReference<BooleanSupplier>();
        var updates = new AtomicInteger();
        var window = new MusicTaskWindow(updates::incrementAndGet,Component.text("Back"), owner -> {
            when(parent.render()).thenAnswer(_ -> {
                parentAction.set(owner.current());
                return MenuScene.screen("directory",Component.text("Playlists"))
                        .content(Ui.text(Component.text(position.get())).key("position").build()).build();
            });
            return parent;
        });
        var handle = mock(FloatingMenuHandle.class);
        window.attach(handle);
        var first = window.render(); var previous = parentAction.get();
        when(child.render()).thenReturn(MenuScene.screen("edit",Component.text("Edit")).content(Ui.column().build()).build());
        window.push(child);
        var editing = window.current();
        assertFalse(previous.getAsBoolean());
        assertEquals(Component.text("Edit"),window.render().title());
        window.back();
        assertFalse(editing.getAsBoolean());
        assertEquals(org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry(first,"position").label(),
                org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry(window.render(),"position").label());
        assertTrue(parentAction.get().getAsBoolean());
        verify(child).close(); verify(parent,never()).close();
        window.close(); window.close();
        verify(parent).close();
        assertFalse(parentAction.get().getAsBoolean());
        assertEquals(2,updates.get());
    }

    @Test void dismissingAChildReleasesEveryPageAndRejectsLateNavigation() {
        var parent = mock(MenuPanel.class); var child = mock(MenuPanel.class); var late = mock(MenuPanel.class);
        var updates = new AtomicInteger();
        var window = new MusicTaskWindow(updates::incrementAndGet,Component.text("Back"),_ -> parent);
        window.push(child); var owned = window.current();
        window.close();
        assertFalse(owned.getAsBoolean());
        window.back(); window.push(late);
        verify(parent).close(); verify(child).close(); verify(late).close();
        assertEquals(1,updates.get());
    }
}
