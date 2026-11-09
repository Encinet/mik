package org.encinet.mik.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.integration.axiom.AxiomGizmoService;
import org.encinet.mik.integration.axiom.AxiomImageService;
import org.encinet.mik.integration.geyser.GeyserService;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.menu.FloatingMenus;
import org.encinet.mik.shared.menu.runtime.FloatingMenuService;
import org.encinet.mik.shared.util.ShutdownSequence;

record SharedServices(
        AxiomGizmoService axiom,
        AxiomImageService axiomImages,
        GeyserService geyser,
        LanguageService language,
        FloatingMenuService menus) {

    static SharedServices enable(
            JavaPlugin plugin, ModuleLifecycle lifecycle) {
        AxiomGizmoService axiomGizmoService = new AxiomGizmoService(plugin);
        lifecycle.startManaged("axiom", axiomGizmoService::enable, axiomGizmoService::disable);

        AxiomImageService axiomImageService = new AxiomImageService(plugin);
        lifecycle.startManaged("axiom-images", axiomImageService::enable, axiomImageService::disable);

        GeyserService geyserService = GeyserService.create(plugin);

        LanguageService languageService = new LanguageService(plugin);
        lifecycle.startManaged("language", languageService::enable, languageService::disable);

        FloatingMenuService floatingMenuService = new FloatingMenuService(
                plugin, axiomGizmoService, axiomImageService, languageService);
        lifecycle.startManaged("floating-menu", () -> {
            floatingMenuService.enable();
            FloatingMenus.install(floatingMenuService);
        }, () -> {
            ShutdownSequence shutdown = new ShutdownSequence();
            shutdown.attempt("floating menu service", floatingMenuService::disable);
            shutdown.attempt("floating menu registration", FloatingMenus::uninstall);
            shutdown.finish("floating menu wiring");
        });

        return new SharedServices(axiomGizmoService, axiomImageService, geyserService, languageService, floatingMenuService);
    }
}
