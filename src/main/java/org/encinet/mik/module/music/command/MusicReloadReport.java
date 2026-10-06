package org.encinet.mik.module.music.command;

import org.encinet.mik.module.music.catalog.MusicLibrary;
import org.encinet.mik.module.music.online.LxSourceService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Outcome of reloading the local catalog and remote source registry. */
public record MusicReloadReport(MusicLibrary.ReloadResult library,
                                LxSourceService.ReloadResult online) {
    public MusicReloadReport {
        Objects.requireNonNull(library, "library");
        Objects.requireNonNull(online, "online");
    }

    public boolean successful() {
        return library.successful() && online.successful();
    }

    public boolean anySuccessful() {
        return library.successful() || online.subscriptions().successful()
                || online.runtimes().successful();
    }

    public List<String> errors() {
        List<String> errors = new ArrayList<>();
        if (!library.successful()) {
            errors.add("local library: " + valueOrUnknown(library.error()));
        }
        if (!online.subscriptions().successful()) {
            errors.add("LX subscriptions: "
                    + valueOrUnknown(online.subscriptions().error()));
        } else if (online.subscriptions().failed() > 0) {
            errors.add("LX subscriptions: " + online.subscriptions().failed()
                    + " update(s) failed");
        }
        if (!online.runtimes().successful()) {
            errors.add("LX runtimes: " + valueOrUnknown(online.runtimes().error()));
        }
        return List.copyOf(errors);
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown error" : value;
    }
}
