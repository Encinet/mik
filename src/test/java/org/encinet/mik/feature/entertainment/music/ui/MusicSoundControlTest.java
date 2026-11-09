package org.encinet.mik.feature.entertainment.music.ui;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.feature.entertainment.music.ui.MusicSoundEdits.Parameter;
import static org.junit.jupiter.api.Assertions.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;

class MusicSoundControlTest {
    @Test void translatedSoundFieldsFitAndKeepTheirEnvelopeWhileValuesChange() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            for (double scale : new double[]{.7, 1, 1.8}) for (int width : new int[]{180, 320, 480}) {
                for (var parameter : Parameter.values()) {
                    UiSize previous = null;
                    for (int value : new int[]{parameter.minimum, (parameter.minimum + parameter.maximum) / 2, parameter.maximum}) {
                        var range = UiRange.fixed(parameter.minimum, parameter.maximum, value).step(parameter.step)
                                .format(number -> Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_SOUND_VALUE,
                                        parameter == Parameter.RANGE ? "range" : "volume", (int) Math.round(number))));
                        var label = Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, parameter.key));
                        var scene = MenuScene.screen("sound").viewport(new UiSize(width, 1024)).content(Ui.form()
                                .field(parameter.key, label, Ui.numberField(label, range, (_,_,_) -> {}).key("value").build()).build()).build();
                        var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(scene, scale), language + " / " + parameter + " / " + scale + " / " + width);
                        assertDisjoint(frame);
                        assertTrue(frame.size().width() <= width + 1e-6);
                        if (previous != null) assertEquals(previous, frame.size(), "adjustment must not resize its enclosing form");
                        previous = frame.size();
                        if (width >= 320) assertEquals(range.text(value), element(frame, "value").node().content().label(),
                                "full unit: " + language + " / " + parameter + " / " + scale + " / " + width + " / " + element(frame, "value").size());
                    }
                }
            }
        }
    }

    @Test void exactInputsUseThePhysicalLimitsAndRejectFractionsAndNonNumbers() {
        for (var parameter : Parameter.values()) {
            assertEquals(parameter.minimum, parameter.parse(" " + parameter.minimum + " "));
            assertEquals(parameter.maximum, parameter.parse(Integer.toString(parameter.maximum)));
            for (var invalid : new String[]{Integer.toString(parameter.minimum - 1), Integer.toString(parameter.maximum + 1), "1.5", "NaN", "", "infinity"})
                assertThrows(IllegalArgumentException.class, () -> parameter.parse(invalid));
        }
    }

    @Test void outputSettingsShareTheirLabelColumnAndKeepBothValuesReadable() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            for (double scale : new double[]{.7, 1, 1.8}) for (int width : new int[]{180, 320, 480}) {
                var fields = Ui.form();
                for (var parameter : new Parameter[]{Parameter.VOLUME, Parameter.RANGE}) {
                    var label = Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, parameter.key));
                    var range = UiRange.fixed(parameter.minimum, parameter.maximum, parameter.maximum)
                            .format(number -> Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_SOUND_VALUE,
                                    parameter.key, (int) Math.round(number))));
                    fields.field(parameter.key, label, Ui.numberField(label, range, (_,_,_) -> {}).key(parameter.key).build());
                }
                var content = fields.sizing(UiSizing.width(UiLength.ZERO,UiLength.ui(width))).build();
                var scene = MusicSceneTestSupport.auxiliary(content,(int) (width + UiTheme.WINDOW.padding().horizontal()));
                var frame = SceneGeometryTestSupport.measure(scene, scale);
                assertDisjoint(frame);
                assertEquals(element(frame, "volume").x() + element(frame, "volume").size().width(),
                        element(frame, "range").x() + element(frame, "range").size().width(), 1e-6,
                        "numeric values align at the shared form edge");
                for (String key : new String[]{"volume", "range"})
                    assertEquals(entry(scene, key).label(), element(frame, key).node().content().label());
            }
        }
    }
}
