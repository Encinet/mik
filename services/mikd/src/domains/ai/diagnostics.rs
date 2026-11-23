use rig_core::error::ErrorReport;
use std::time::Instant;
use uuid::Uuid;

#[derive(Clone, Copy)]
pub(super) struct TurnLog {
    id: Uuid,
    platform: &'static str,
    started: Instant,
}

impl TurnLog {
    pub(super) fn new(id: Uuid, platform: &'static str) -> Self {
        Self {
            id,
            platform,
            started: Instant::now(),
        }
    }

    pub(super) fn event(self, stage: &'static str, reason: &'static str) {
        self.write(stage, reason, None);
    }

    pub(super) fn provider(self, stage: &'static str, report: &ErrorReport) {
        self.write(stage, report.kind.code(), report.http_status);
    }

    fn write(self, stage: &'static str, reason: &'static str, http_status: Option<u16>) {
        let status =
            http_status.map_or_else(String::new, |status| format!(" http_status={status}"));
        eprintln!(
            "mikd AI turn={} platform={} stage={stage} reason={reason} elapsed_ms={}{status}",
            self.id,
            self.platform,
            self.started.elapsed().as_millis(),
        );
    }
}
