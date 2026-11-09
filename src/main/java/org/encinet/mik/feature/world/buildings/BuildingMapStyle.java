package org.encinet.mik.feature.world.buildings;

/** The card and marker geometry share one set of dimensions, including the transparent hit area. */
final class BuildingMapStyle {
    static final int POPUP_WIDTH = 304;
    static final int POPUP_MAX_HEIGHT = 440;
    static final int ICON_WIDTH = 32;
    static final int ICON_HEIGHT = 40;
    static final int ICON_PADDING_X = 6;
    static final int ICON_PADDING_Y = 4;

    static final String CSS = """
            .leaflet-popup:has(.mik-building-card) {
              --mik-card-background:#191e1c;--mik-card-text:#f2f5f0;
              --mik-card-muted:#b7c2ba;--mik-card-accent:#c5d9bf;color-scheme:dark;
            }
            .leaflet-popup:has(.mik-building-card) .leaflet-popup-content-wrapper {
              padding:0;overflow:hidden;background:var(--mik-card-background);color:var(--mik-card-text);
              border:1px solid #ffffff24;border-radius:16px;box-shadow:0 12px 40px #0006;
            }
            .leaflet-popup:has(.mik-building-card) .leaflet-popup-content {
              margin:0;border:0;overflow-x:hidden;overflow-y:auto;overscroll-behavior:contain;
              max-height:min(%1$dpx,calc(100dvh - 96px - env(safe-area-inset-top) - env(safe-area-inset-bottom)));
              scrollbar-width:thin;scroll-padding-block:56px 16px;
            }
            .leaflet-popup:has(.mik-building-card) .leaflet-popup-tip {background:var(--mik-card-background)}
            .leaflet-popup:has(.mik-building-card) a.leaflet-popup-close-button {
              top:8px;right:8px;width:44px;height:44px;border-radius:50%%;
              background:#141916df;color:#fff;line-height:44px;font-size:24px;
              touch-action:manipulation;-webkit-tap-highlight-color:#c5d9bf40;
            }
            .leaflet-popup:has(.mik-building-card) a.leaflet-popup-close-button:hover {background:#27382c;color:#fff}
            .leaflet-popup:has(.mik-building-card) a.leaflet-popup-close-button:focus-visible {
              outline:2px solid var(--mik-card-text);outline-offset:-3px;
            }
            .mik-building-card,.mik-building-card * {box-sizing:border-box}
            .mik-building-card {
              width:min(%2$dpx,calc(100vw - 128px - env(safe-area-inset-left) - env(safe-area-inset-right)));
              max-width:100%%;font:14px/1.55 system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif;
            }
            .mik-building-card img {display:block;width:100%%;height:auto;aspect-ratio:16/9;object-fit:cover;background:#27312b}
            .mik-building-card-body {padding:18px}
            .mik-building-card h2 {margin:6px 0;font-size:18px;font-weight:600;line-height:1.45;text-wrap:pretty;overflow-wrap:anywhere}
            .mik-building-card p {margin:8px 0;overflow-wrap:anywhere}
            .mik-building-card .mik-building-muted {color:var(--mik-card-muted);font-size:13px}
            .mik-building-card .mik-building-type {color:#bed9bf;font-size:13px}
            .mik-building-card:not(:has(>img)) :is(.mik-building-type,h2) {padding-inline-end:36px}
            .mik-building-card .mik-building-card-link {
              display:flex;min-width:0;align-items:center;justify-content:center;text-align:center;
              min-height:44px;margin-top:16px;padding:6px 10px;border-radius:10px;background:var(--mik-card-accent);
              color:#18221b;text-decoration:none;font-weight:600;overflow-wrap:anywhere;
              touch-action:manipulation;-webkit-tap-highlight-color:#c5d9bf40;
            }
            .mik-building-card .mik-building-card-link:hover {background:#d9e7d5}
            .mik-building-card .mik-building-card-link:active {background:#e1eddc}
            .mik-building-card a:focus-visible {outline:2px solid var(--mik-card-text);outline-offset:3px}
            .mik-building-tooltip {display:block;width:min(260px,calc(100vw - 48px));white-space:normal;overflow-wrap:anywhere;line-height:1.5}
            .mik-building-tooltip-title,.mik-building-tooltip-en {
              display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden;
            }
            .mik-building-tooltip-en {margin-top:2px;font-size:12px}
            .leaflet-container img.leaflet-marker-icon[src$="/mik-building-pin.png"] {
              box-sizing:content-box;padding:%3$dpx %4$dpx;transition:none;
              filter:drop-shadow(0 2px 3px #0009);touch-action:manipulation;
            }
            .leaflet-container img.leaflet-marker-icon[src$="/mik-building-pin.png"]:focus-visible {
              outline:2px solid #f2f5f0;outline-offset:1px;border-radius:12px;
            }
            .leaflet-container.leaflet-zoom-anim img.leaflet-marker-icon[src$="/mik-building-pin.png"] {
              transition:transform .25s cubic-bezier(0,0,.25,1);
            }
            @media (max-height:480px) {
              .mik-building-card img {height:min(120px,28dvh);aspect-ratio:auto}
            }
            @media (prefers-reduced-motion:reduce) {
              .leaflet-container.leaflet-zoom-anim img.leaflet-marker-icon[src$="/mik-building-pin.png"],
              .leaflet-fade-anim .leaflet-popup:has(.mik-building-card) {transition:none}
            }
            """.formatted(POPUP_MAX_HEIGHT, POPUP_WIDTH, ICON_PADDING_Y, ICON_PADDING_X);

    private BuildingMapStyle() { }
}
