package org.encinet.mik.feature.world.buildings;

import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.util.stream.Collectors;

final class BuildingMapPopup {
    private final LanguageService languages;

    BuildingMapPopup(LanguageService languages) {
        this.languages = languages;
    }

    String label() {
        return languages.t(Language.ZH_CN, Message.BUILDING_MAP_LAYER) + " · "
                + languages.t(Language.EN_US, Message.BUILDING_MAP_LAYER);
    }

    String render(BuildingCatalog.Entry entry) {
        Message type = switch (entry.type()) {
            case "replica" -> Message.BUILDING_MAP_TYPE_REPLICA;
            case "derivative" -> Message.BUILDING_MAP_TYPE_DERIVATIVE;
            default -> Message.BUILDING_MAP_TYPE_ORIGINAL;
        };
        String builders = entry.builders().stream().map(BuildingCatalog.Builder::name).collect(Collectors.joining(", "));
        StringBuilder html = new StringBuilder("<article class=\"mik-building-card\" lang=\"zh-CN\">");
        if (!entry.cover().isEmpty()) {
            html.append("<img loading=\"eager\" decoding=\"async\" referrerpolicy=\"no-referrer\" width=\"304\" height=\"171\" alt=\"")
                    .append(escape(entry.nameZh())).append("\" src=\"")
                    .append(escape(entry.cover())).append("\">");
        }
        html.append("<div class=\"mik-building-card-body\"><div class=\"mik-building-type\">")
                .append(escape(languages.t(Language.ZH_CN, type))).append(" · ")
                .append(escape(languages.t(Language.EN_US, type)))
                .append("</div><h2 lang=\"zh-CN\">").append(escape(entry.nameZh())).append("</h2>")
                .append("<p lang=\"en\" class=\"mik-building-muted\">").append(escape(entry.nameEn())).append("</p><p>")
                .append(escape(languages.t(Language.ZH_CN, Message.BUILDING_MAP_BUILDERS, builders))).append("</p>");
        if (!entry.originalAuthor().isEmpty()) {
            html.append("<p class=\"mik-building-muted\">")
                    .append(escape(languages.t(Language.ZH_CN, Message.BUILDING_MAP_ORIGINAL_AUTHOR, entry.originalAuthor())))
                    .append("</p>");
        }
        html.append("<p class=\"mik-building-muted\" translate=\"no\">").append(escape(entry.world())).append(" · ")
                .append(entry.x()).append(", ").append(entry.y()).append(", ").append(entry.z())
                .append("</p>");
        link(html, entry.id());
        return html.append("</div></article>").toString();
    }

    String tooltip(BuildingCatalog.Entry entry) {
        return "<span class=\"mik-building-tooltip\" lang=\"zh-CN\"><span class=\"mik-building-tooltip-title\">" + escape(entry.nameZh()) + "</span>"
                + "<span class=\"mik-building-tooltip-en\" lang=\"en\">" + escape(entry.nameEn()) + "</span></span>";
    }

    private void link(StringBuilder html, String id) {
        html.append("<a class=\"mik-building-card-link\" target=\"_blank\" rel=\"noopener noreferrer\" href=\"https://mcmik.top/buildings/")
                .append(id).append("\">")
                .append(escape(languages.t(Language.DEFAULT, Message.BUILDING_MAP_OPEN))).append("</a>");
    }

    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
