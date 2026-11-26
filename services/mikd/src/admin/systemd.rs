use std::{io, process::Stdio, time::Duration};
use tokio::{
    io::{AsyncRead, AsyncReadExt},
    process::Command,
    time::timeout,
};

pub(super) const UNIT: &str = "mikd.service";
const QUERY_TIMEOUT: Duration = Duration::from_secs(5);
const ACTION_TIMEOUT: Duration = Duration::from_mins(8);
const MAX_LOG_BYTES: usize = 512 * 1024;

#[derive(Clone, Copy)]
pub(super) enum Action {
    Start,
    Stop,
    Restart,
}

impl Action {
    pub fn verb(self) -> &'static str {
        match self {
            Self::Start => "start",
            Self::Stop => "stop",
            Self::Restart => "restart",
        }
    }
}

#[derive(Default)]
pub(super) struct Unit {
    pub load: String,
    pub active: String,
    pub sub: String,
    pub result: String,
}

pub(super) struct JournalOutput {
    pub entries: String,
    pub warning: Option<String>,
}

impl Unit {
    pub fn allows(&self, action: Action) -> bool {
        self.load == "loaded"
            && match action {
                Action::Start => matches!(self.active.as_str(), "inactive" | "failed"),
                Action::Stop => matches!(
                    self.active.as_str(),
                    "active" | "activating" | "reloading" | "refreshing"
                ),
                Action::Restart => self.active == "active",
            }
    }
}

pub(super) async fn status() -> io::Result<Unit> {
    let mut command = systemctl();
    command.args([
        "show",
        "--property=LoadState,ActiveState,SubState,Result",
        "--",
        UNIT,
    ]);
    let output = run(command, QUERY_TIMEOUT, 16 * 1024).await?;
    let stdout = String::from_utf8_lossy(&output.stdout);
    let mut unit = Unit::default();
    for (key, value) in stdout.lines().filter_map(|line| line.split_once('=')) {
        match key {
            "LoadState" => unit.load = value.into(),
            "ActiveState" => unit.active = value.into(),
            "SubState" => unit.sub = value.into(),
            "Result" => unit.result = value.into(),
            _ => {}
        }
    }
    if unit.load.is_empty() || unit.active.is_empty() {
        return Err(io::Error::other(
            output
                .diagnostic()
                .unwrap_or_else(|| "systemd did not report the unit state".into()),
        ));
    }
    Ok(unit)
}

pub(super) async fn control(action: Action) -> io::Result<()> {
    let mut command = systemctl();
    command.args([action.verb(), "--", UNIT]);
    run(command, ACTION_TIMEOUT, 16 * 1024).await.map(|_| ())
}

pub(super) async fn recent_logs() -> io::Result<JournalOutput> {
    let mut command = Command::new("/usr/bin/journalctl");
    command.args([
        "--system",
        "--no-pager",
        "--lines=200",
        "--output=short-iso",
        "--unit",
        UNIT,
    ]);
    let output = run(command, QUERY_TIMEOUT, MAX_LOG_BYTES).await?;
    let warning = output.diagnostic();
    let mut entries = String::from_utf8_lossy(&output.stdout).into_owned();
    if entries.trim() == "-- No entries --" {
        entries.clear();
    }
    Ok(JournalOutput { entries, warning })
}

fn systemctl() -> Command {
    let mut command = Command::new("/usr/bin/systemctl");
    command.args(["--system", "--no-pager", "--no-ask-password"]);
    command
}

struct Output {
    stdout: Vec<u8>,
    stderr: Vec<u8>,
}

impl Output {
    fn diagnostic(&self) -> Option<String> {
        let stderr = String::from_utf8_lossy(&self.stderr);
        let message = stderr.trim();
        (!message.is_empty()).then(|| message.to_owned())
    }
}

async fn run(mut command: Command, deadline: Duration, limit: usize) -> io::Result<Output> {
    let mut child = command
        .kill_on_drop(true)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()?;
    let stdout = child
        .stdout
        .take()
        .ok_or_else(|| io::Error::other("missing command stdout"))?;
    let stderr = child
        .stderr
        .take()
        .ok_or_else(|| io::Error::other("missing command stderr"))?;
    let (stdout, stderr, status) = timeout(deadline, async {
        tokio::try_join!(
            read_limit(stdout, limit),
            read_limit(stderr, 16 * 1024),
            child.wait()
        )
    })
    .await??;
    let output = Output { stdout, stderr };
    if status.success() {
        Ok(output)
    } else {
        Err(io::Error::other(output.diagnostic().unwrap_or_else(|| {
            format!("host command exited with {status}")
        })))
    }
}

async fn read_limit(pipe: impl AsyncRead + Unpin, limit: usize) -> io::Result<Vec<u8>> {
    let mut text = Vec::new();
    pipe.take((limit + 1) as u64).read_to_end(&mut text).await?;
    if text.len() > limit {
        return Err(io::Error::other(
            "host command output exceeded its size limit",
        ));
    }
    Ok(text)
}
