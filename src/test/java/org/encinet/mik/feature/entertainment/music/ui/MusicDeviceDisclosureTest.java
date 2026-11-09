package org.encinet.mik.feature.entertainment.music.ui;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicDeviceDisclosureTest {
    @Test void expandedIdentityFieldsKeepSoundControlsAndDeviceActionsAcrossLanguageAndReadingBoundaries() throws Exception {
        for (var locale : Language.values()) {
            var bundle = FluentTestSupport.bundle(locale);
            java.util.function.Function<String,Component> label = key -> Component.text(FluentTestSupport.text(bundle,Message.MUSIC_SPACE_LABEL,key));
            for (boolean expanded : new boolean[]{false,true}) for (boolean editing : new boolean[]{false,true})
                for (double font : new double[]{.7,1.8}) for (double scale : new double[]{.7,1.3}) for (int fov : new int[]{30,110}) {
                    Component title = label.apply("name").append(Component.text(" · ")).append(label.apply("alias"));
                    Component action = Component.text(FluentTestSupport.text(bundle,expanded ? Message.MENU_COLLAPSE : Message.MENU_EXPAND));
                    var toggle = Ui.button(expanded ? UiIcons.CHEVRON_UP : UiIcons.CHEVRON_DOWN,action).key("toggle").selected(expanded)
                            .primary((_,_) -> fail("Measurement must not change expansion"));
                    if (editing) toggle.disabled(Component.text(FluentTestSupport.text(bundle,Message.MENU_FINISH_EDITING)));
                    var identity = UiTemplates.disclosure(UiKey.named("device-identity"),Ui.text(title).key("summary").singleLine().build(),
                            toggle.build(),expanded,() -> {
                                var fields = new java.util.ArrayList<UiNode>();
                                for (String key : List.of("name","alias")) fields.add(Ui.form().key("device-" + key).field("value",label.apply(key),
                                        Ui.textField(Component.text("Long distinguishing name 名称 ".repeat(4))).key("value").maxLines(1).build()).build());
                                return MusicDeviceScenes.identityFields(fields);
                            });
                    var sound = Ui.form().key("sound-settings");
                    for (String key : List.of("volume","range")) sound.field(key,label.apply(key),
                            Ui.numberField(label.apply(key),UiRange.fixed(0,100,50),(_,_,_) -> {}).key(key).build());
                    var content = MusicWorkspace.column("device-inspector")
                            .child(MusicWorkspaceComposition.panelHeading(label.apply("device-settings"),Ui.button(UiIcons.CROSS,label.apply("close-panel")).key("close-panel").build()))
                            .child(MusicWorkspaceComposition.deviceLocation(Component.text("Front right · 12 blocks"),Component.text("world · X -20 · Y 64 · Z 12"),null))
                            .child(MusicWorkspace.tools("device-tabs").child(Ui.button(label.apply("device-queue")).key("queue").build())
                                    .child(Ui.button(label.apply("device-settings")).key("settings").build()).build())
                            .child(sound.build()).child(identity).child(MusicWorkspace.tools("device-actions")
                                    .child(Ui.button(label.apply("forget")).key("forget").build()).child(Ui.button(label.apply("eject")).key("eject").build()).build()).build();
                    var scene = MusicSceneTestSupport.auxiliary(content,(int) MusicWorkspaceComposition.VIEWPORT.width());
                    var frame = SceneGeometryTestSupport.readableFrame(scene,font,new FloatingMenuFieldOfView(fov),scale);
                    assertDisjoint(frame);
                    for (String key : List.of("volume","range","forget","eject","close-panel","toggle")) assertNotNull(element(frame,key));
                    assertEquals(expanded,element(frame,"toggle").node().input().selected());
                    assertEquals(editing,element(frame,"toggle").node().input().disabledReason().isPresent());
                    assertEquals(expanded,frame.elements().stream().anyMatch(item -> item.path().contains("/device-name/")));
                    assertEquals(expanded,frame.elements().stream().anyMatch(item -> item.path().contains("/device-alias/")));
                }
        }
    }
}
