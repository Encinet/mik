package org.encinet.mik.feature.community.communication;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.ui.UiNode;

/** Lets host composition tests embed the feature's actual localized preview. */
public final class AnnouncementPreviewTestSupport {
    private AnnouncementPreviewTestSupport() { }

    public static UiNode content(Language language, String body) throws Exception {
        var bundle = FluentTestSupport.bundle(language);
        return AnnouncementPreview.content(new AnnouncementCatalog.Announcement(1, body),
                Component.text(FluentTestSupport.text(bundle, Message.ANNOUNCEMENT_LATEST)),
                Component.text(FluentTestSupport.text(bundle, Message.ANNOUNCEMENT_VIEW_ALL)), (_, _) -> {});
    }
}
