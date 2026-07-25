package org.encinet.mik.module.music;

import org.encinet.mik.module.music.catalog.MusicLibrary;
import org.encinet.mik.module.music.online.LxSourceService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicModuleReloadReportTest {

    @Test
    void reportsCompleteSuccessWithoutIssues() {
        MusicModule.ReloadReport report = report(
                new MusicLibrary.ReloadResult(true, 12, null),
                new LxSourceService.SubscriptionReload(true, 3, 1, 0, null),
                new LxSourceService.RuntimeReload(true, 2, 3, null));

        assertTrue(report.successful());
        assertTrue(report.anySuccessful());
        assertEquals(List.of(), report.errors());
    }

    @Test
    void reportsFailedSubscriptionUpdatesAsPartialSuccess() {
        MusicModule.ReloadReport report = report(
                new MusicLibrary.ReloadResult(true, 12, null),
                new LxSourceService.SubscriptionReload(true, 3, 1, 1, null),
                new LxSourceService.RuntimeReload(true, 2, 3, null));

        assertFalse(report.successful());
        assertTrue(report.anySuccessful());
        assertEquals(List.of("LX subscriptions: 1 update(s) failed"), report.errors());
    }

    @Test
    void aggregatesIndependentFailuresAndNormalizesMissingMessages() {
        MusicModule.ReloadReport report = report(
                new MusicLibrary.ReloadResult(false, 4, "disk unavailable"),
                new LxSourceService.SubscriptionReload(false, 2, 0, 0, " "),
                new LxSourceService.RuntimeReload(false, 1, 2, null));

        assertFalse(report.successful());
        assertFalse(report.anySuccessful());
        assertEquals(List.of(
                "local library: disk unavailable",
                "LX subscriptions: unknown error",
                "LX runtimes: unknown error"), report.errors());
    }

    private static MusicModule.ReloadReport report(
            MusicLibrary.ReloadResult library,
            LxSourceService.SubscriptionReload subscriptions,
            LxSourceService.RuntimeReload runtimes) {
        return new MusicModule.ReloadReport(library,
                new LxSourceService.ReloadResult(subscriptions, runtimes));
    }
}
