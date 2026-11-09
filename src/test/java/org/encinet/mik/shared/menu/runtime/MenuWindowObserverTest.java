package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class MenuWindowObserverTest {
    @Test void compilationIsPureAndPublishedWindowsDeduplicateByDataAndRange() {
        var received = new ArrayList<Integer>();
        var identity = new Object();
        var observer = new MenuWindowObserver();
        var frame = frame(identity,0,received);
        assertTrue(received.isEmpty());
        observer.publish(frame,() -> true,error -> fail(error));
        assertEquals(1,received.size());
        observer.publish(frame(identity,0,received),() -> true,error -> fail(error));
        assertEquals(1,received.size());
        observer.publish(frame(identity,1,received),() -> true,error -> fail(error));
        assertEquals(2,received.size());
        observer.publish(frame(new Object(),1,received),() -> true,error -> fail(error));
        assertEquals(3,received.size());
    }
    @Test void staleWindowsDoNotDeliverAndResumePublishesCurrentRangeAgain() {
        var received = new ArrayList<Integer>();
        var observer = new MenuWindowObserver();
        var frame = frame(new Object(),0,received);
        observer.publish(frame,() -> false,error -> fail(error));
        assertTrue(received.isEmpty());
        observer.publish(frame,() -> true,error -> fail(error));
        observer.clear();
        observer.publish(frame,() -> true,error -> fail(error));
        assertEquals(2,received.size());
    }
    @Test void reentryRetiresRemainingCallbacksAndObserverFailureDoesNotBreakPublication() {
        var current = new AtomicBoolean(true);
        var root = Ui.column().child(window("one",new Object(),_ -> current.set(false)))
                .child(window("two",new Object(),_ -> fail("retired frame"))).build();
        var frame = compile(root);
        new MenuWindowObserver().publish(frame,current::get,error -> fail(error));
        assertFalse(current.get());
        var failures = new ArrayList<RuntimeException>();
        var broken = compile(window("failure",new Object(),_ -> { throw new IllegalStateException("observer"); }));
        new MenuWindowObserver().publish(broken,() -> true,failures::add);
        assertEquals(1,failures.size());
    }
    private static UiFrame frame(Object identity, int page, ArrayList<Integer> received) {
        return compile(Ui.windowedList(100,Ui.text(net.kyori.adventure.text.Component.text("row")).build(),index -> Ui.text(net.kyori.adventure.text.Component.text("row")).key("row:" + index).build())
                .page(page,_ -> {}).pager(_ -> Ui.column().build()).observeWindow(identity,value -> received.add(value.fromIndex())).build());
    }
    private static UiNode window(String key, Object identity, java.util.function.Consumer<org.encinet.mik.shared.menu.FloatingMenuPage> ready) {
        return Ui.windowedList(1,Ui.text(net.kyori.adventure.text.Component.text("row")).build(),index -> Ui.text(net.kyori.adventure.text.Component.text("row")).key("row:" + index).build())
                .key(key).pager(_ -> Ui.column().build()).observeWindow(identity,ready).build();
    }
    private static UiFrame compile(UiNode root) {
        return UiCompiler.compile(root,UiConstraints.bounded(400,200),(_,_) -> new UiCompiler.ElementMeasurement(new UiSize(30,20),160));
    }
}
