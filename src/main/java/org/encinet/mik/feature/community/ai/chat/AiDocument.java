package org.encinet.mik.feature.community.ai.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.encinet.mik.protocol.AssistantBlock;
import org.encinet.mik.protocol.AssistantCodeBlock;
import org.encinet.mik.protocol.AssistantInline;
import org.encinet.mik.protocol.AssistantList;
import org.encinet.mik.protocol.AssistantRow;
import org.encinet.mik.protocol.AssistantTable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

final class AiDocument {
    private static final int MAX_FOLLOW_UPS = 2;
    private static final Pattern FOLLOW_UP = Pattern.compile(
            "[\\p{L}\\p{N}¿][^\\p{C}\\p{Zl}\\p{Zp}§<>/@]{0,59}");
    private static final Pattern CODE_LANGUAGE = Pattern.compile("[A-Za-z0-9_+#\\-]{1,32}");

    private final boolean nativeInteraction;
    private final Function<String, ClickEvent<?>> followUpClick;
    private final Set<String> followUps = new LinkedHashSet<>();

    AiDocument(boolean nativeInteraction, Function<String, ClickEvent<?>> followUpClick) {
        this.nativeInteraction = nativeInteraction;
        this.followUpClick = followUpClick;
    }

    boolean hasCallbacks() {
        return nativeInteraction && !followUps.isEmpty();
    }

    Component blocks(List<AssistantBlock> blocks, String first, String prefix) {
        var result = Component.text();
        boolean displayed = false;
        for (var block : blocks) {
            var content = block(block, displayed ? "\n" + prefix.stripTrailing() + "\n" + prefix : first, prefix);
            if (content == null) continue;
            result.append(content);
            displayed = true;
        }
        return displayed ? result.build() : null;
    }

    private Component block(AssistantBlock block, String lead, String prefix) {
        return switch (block.getContentCase()) {
            case PARAGRAPH -> line(lead, inline(block.getParagraph(), prefix));
            case HEADING -> {
                var heading = block.getHeading();
                var body = inline(heading.getBody(), prefix);
                yield body == null ? null : line(lead, body.decorate(TextDecoration.BOLD)
                        .color(heading.getLevel() == 1 ? NamedTextColor.AQUA : NamedTextColor.YELLOW));
            }
            case BLOCK_QUOTE -> {
                var quote = blocks(block.getBlockQuote().getBlocksList(), lead + "│ ", prefix + "│ ");
                yield quote == null ? null : quote.decorate(TextDecoration.ITALIC);
            }
            case LIST -> list(block.getList(), lead, prefix);
            case CODE -> line(lead, code(block.getCode(), prefix));
            case TABLE -> table(block.getTable(), lead, prefix);
            case DIVIDER -> line(lead, Component.text("────", NamedTextColor.AQUA));
            case CONTENT_NOT_SET -> null;
        };
    }

    private Component list(AssistantList list, String first, String prefix) {
        var result = Component.text();
        long number = Integer.toUnsignedLong(list.getFirstNumber());
        boolean displayed = false;
        for (var item : list.getChildrenList()) {
            var marker = (list.hasFirstNumber() ? number + ". " : "• ")
                    + (item.hasChecked() ? item.getChecked() ? "☑ " : "☐ " : "");
            var lead = (displayed ? "\n" + prefix : first) + marker;
            var content = blocks(item.getBlocksList(), lead, prefix + " ".repeat(marker.length()));
            if (content == null) {
                if (!item.hasChecked()) continue;
                content = Component.text(lead.stripTrailing(), NamedTextColor.AQUA);
            }
            result.append(content);
            number++;
            displayed = true;
        }
        return displayed ? result.build() : null;
    }

    private Component code(AssistantCodeBlock code, String prefix) {
        if (code.getText().isBlank()) return null;
        var body = Component.text(AiAnswerText.withLinePrefix(code.getText(), prefix), NamedTextColor.GREEN);
        return nativeInteraction && CODE_LANGUAGE.matcher(code.getLanguage()).matches()
                ? body.hoverEvent(HoverEvent.showText(Component.text(code.getLanguage()))) : body;
    }

    private Component table(AssistantTable table, String lead, String prefix) {
        if (!table.hasHeader()) return null;
        var lines = Component.text().append(line(lead, row(table.getHeader(), prefix)
                .color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)));
        for (var row : table.getRowsList()) lines.append(line("\n" + prefix, row(row, prefix)));
        return lines.build();
    }

    private Component row(AssistantRow row, String prefix) {
        var result = Component.text();
        for (int column = 0; column < row.getCellsCount(); column++) {
            if (column > 0) result.append(Component.text(" │ ", NamedTextColor.AQUA));
            var cell = inline(row.getCells(column), prefix);
            if (cell != null) result.append(cell);
        }
        return result.build();
    }

    private static Component line(String lead, Component body) {
        if (body == null || lead.isEmpty()) return body;
        return Component.text().append(Component.text(lead, NamedTextColor.AQUA)).append(body).build();
    }

    private Component inline(AssistantInline inline, String prefix) {
        var content = Component.text();
        boolean readable = false;
        for (var part : inline.getPartsList()) {
            var text = new StringBuilder();
            part.getSpansList().forEach(span -> text.append(span.getText()));
            if (text.isEmpty()) continue;
            var rendered = AiAnswerText.render(part, nativeInteraction, prefix);
            var label = text.toString();
            if (!label.isBlank()) {
                readable = true;
                rendered = switch (part.getTargetCase()) {
                    case URL -> AiWebLinks.render(rendered, label, part.getUrl(), nativeInteraction);
                    case ASK_LABEL -> followUp(rendered, label);
                    case TARGET_NOT_SET -> rendered;
                };
            }
            content.append(rendered);
        }
        return readable ? content.build() : null;
    }

    private Component followUp(Component label, String prompt) {
        if (!prompt.equals(prompt.strip()) || !FOLLOW_UP.matcher(prompt).matches()
                || followUps.size() == MAX_FOLLOW_UPS || !followUps.add(prompt)) {
            return label;
        }
        return nativeInteraction ? label.color(NamedTextColor.AQUA).decorate(TextDecoration.UNDERLINED)
                .clickEvent(followUpClick.apply(prompt))
                : Component.text("/ai ", NamedTextColor.AQUA).append(label);
    }
}
