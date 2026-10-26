package org.encinet.mik.feature.player.guidance;

import org.encinet.mik.shared.i18n.Message;

/** Main-thread registration capability for one feature, owned and closed by bootstrap. */
public interface TipScope extends AutoCloseable {
    <C> Tip.Builder<C> define(String id);

    /** Localized MiniMessage text; native item hovers, translations and command actions stay in the resource. */
    default <C> Tip.Builder<C> define(String id, Message message) {
        return this.<C>define(id).message(message);
    }

    @Override
    void close();
}
