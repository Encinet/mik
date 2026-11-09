package org.encinet.mik.feature.world.plot;

import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.Message;

/** One rendering and validation policy for both the editor preview and successful travel. */
final class PlotDestinationTitles {
    static final int SOURCE_LIMIT = 512;
    private static final MiniMessage FORMAT = MiniMessage.builder().tags(TagResolver.resolver(
            StandardTags.color(), StandardTags.decorations(), StandardTags.gradient(),
            StandardTags.rainbow(), StandardTags.reset())).build();
    private static final Title.Times TIMES = Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofMillis(700));

    private PlotDestinationTitles() { }

    static Component render(String value) { return FORMAT.deserialize(value); }

    static void validate(String title, String subtitle) {
        validate(title, 32, Message.PLOT_DESTINATION_ERROR_TITLE);
        validate(subtitle, 64, Message.PLOT_DESTINATION_ERROR_SUBTITLE);
    }

    private static void validate(String source, int visibleLimit, Message error) {
        if (source.codePointCount(0, source.length()) > SOURCE_LIMIT
                || source.codePoints().anyMatch(point -> Character.isISOControl(point)
                    || Character.getType(point) == Character.LINE_SEPARATOR || Character.getType(point) == Character.PARAGRAPH_SEPARATOR))
            throw new PlotProblem(error);
        String visible = PlainTextComponentSerializer.plainText().serialize(render(source));
        if (visible.codePointCount(0, visible.length()) > visibleLimit
                || !source.isEmpty() && visible.isBlank()) throw new PlotProblem(error);
    }

    static void show(Player player, PlotDestination point) {
        if (!point.title().isEmpty() || !point.subtitle().isEmpty())
            player.showTitle(Title.title(render(point.title()), render(point.subtitle()), TIMES));
    }
}
