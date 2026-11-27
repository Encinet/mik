use crate::{
    console::{CommandOutcome, LogPage, LogPosition, ProviderStatus, RESULT_BUDGET, Target},
    transport::admin::{self, CallError, Command, Reply},
};
use std::{io, path::Path, time::Duration};
use tokio::time::timeout;

const UNEXPECTED: &str = "Unexpected console reply";
const READ_TIMEOUT: Duration = RESULT_BUDGET.saturating_add(Duration::from_secs(2));

pub(super) async fn providers(socket: &Path) -> io::Result<Vec<ProviderStatus>> {
    match read(socket, Command::ListConsoles).await? {
        Reply::Consoles(providers) => Ok(providers),
        _ => Err(io::Error::other(UNEXPECTED)),
    }
}

pub(super) async fn logs(
    socket: &Path,
    target: Target,
    position: LogPosition,
) -> io::Result<LogPage> {
    match read(socket, Command::ReadConsoleLogs { target, position }).await? {
        Reply::ConsoleLogs(page) => Ok(page),
        _ => Err(io::Error::other(UNEXPECTED)),
    }
}

async fn read(socket: &Path, command: Command) -> io::Result<Reply> {
    match timeout(READ_TIMEOUT, admin::call(socket, command)).await?? {
        Reply::Error(error) => Err(io::Error::other(error)),
        reply => Ok(reply),
    }
}

pub(super) enum RunError {
    Failed(io::Error),
    Unconfirmed(io::Error),
}

pub(super) async fn run(
    socket: &Path,
    target: Target,
    line: String,
) -> Result<CommandOutcome, RunError> {
    match admin::call_with_outcome(socket, Command::SubmitConsoleInput { target, line }).await {
        Ok(Reply::ConsoleCommand(outcome)) => Ok(outcome),
        Ok(Reply::Error(error)) if Reply::is_unconfirmed(&error) => {
            Err(RunError::Unconfirmed(io::Error::other(error)))
        }
        Ok(Reply::Error(error)) => Err(RunError::Failed(io::Error::other(error))),
        Ok(_) => Err(RunError::Unconfirmed(io::Error::other(UNEXPECTED))),
        Err(CallError::NotSent(error)) => Err(RunError::Failed(error)),
        Err(CallError::Unconfirmed(error)) => Err(RunError::Unconfirmed(error)),
    }
}
