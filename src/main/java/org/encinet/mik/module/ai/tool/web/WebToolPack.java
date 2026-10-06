package org.encinet.mik.module.ai.tool.web;

import org.encinet.mik.module.ai.tool.AiTool;
import org.encinet.mik.module.ai.tool.AiToolPack;

import java.util.List;
import java.util.Objects;

/** Live web capabilities; the concrete search backend remains configurable. */
public final class WebToolPack {
    private WebToolPack() {
    }

    public static AiToolPack create(List<? extends AiTool> tools) {
        List<AiTool> installed = List.copyOf(Objects.requireNonNull(tools, "tools"));
        return new AiToolPack("web",
                "Live public-web search plus safe page fetching as readable Markdown-like text.",
                List.of("web", "search", "fetch", "browse", "open", "link", "internet",
                        "current", "latest", "网页", "網頁", "搜索", "搜尋", "抓取",
                        "链接", "連結", "最新", "ウェブ", "検索", "リンク", "웹",
                        "검색", "링크", "recherche", "lien", "suche", "link", "buscar",
                        "enlace", "поиск", "ссылка"), installed);
    }
}
