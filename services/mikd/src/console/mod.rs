//! Routes administrative work to the plugin that registered each console.
mod logs;
mod requests;

pub(crate) use logs::{LogPage, LogPosition};
pub(crate) use requests::RESULT_BUDGET;

use mik_protocol::wire::{
    ConsoleCommand, ConsoleCommandReceipt, ConsoleCommandWork, ConsoleLogQuery, ConsoleLogRead,
    ConsoleLogReceipt, ConsoleLogWork, ConsoleRegistration, console_command_receipt::Outcome,
};
use requests::{Failure, Requests, invalid_receipt};
use serde::{Deserialize, Serialize};
use std::{
    collections::BTreeMap,
    fmt, io,
    sync::{Arc, Mutex, MutexGuard, PoisonError},
    time::{Duration, Instant},
};

pub(crate) const MAX_PROVIDERS: usize = 16;
const MAX_COMMAND_BYTES: usize = 4096;

#[derive(Default)]
pub(crate) struct Console {
    providers: Mutex<BTreeMap<ConsoleId, Provider>>,
}

#[derive(Clone, Deserialize, Eq, Ord, PartialEq, PartialOrd, Serialize)]
pub(crate) struct ConsoleId {
    pub instance: String,
    pub provider: String,
}

impl fmt::Display for ConsoleId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}/{}", self.instance, self.provider)
    }
}

struct Provider {
    startup: String,
    registration: ConsoleRegistration,
    commands: Option<Connection<CommandRequests>>,
    logs: Option<Connection<LogRequests>>,
    disconnected_at: Instant,
}

struct Connection<R> {
    session: String,
    requests: Arc<R>,
}

type CommandRequests = Requests<String, CommandOutcome>;
type LogRequests = Requests<ConsoleLogQuery, io::Result<LogPage>>;

#[derive(Clone, Copy)]
pub(crate) enum Lane {
    Commands,
    Logs,
}

enum Channel {
    Commands(Arc<CommandRequests>),
    Logs(Arc<LogRequests>),
}

pub(crate) struct Session<'a> {
    console: &'a Console,
    id: ConsoleId,
    channel: Channel,
}

#[derive(Deserialize, Serialize)]
pub(crate) struct ProviderStatus {
    pub id: ConsoleId,
    pub label: String,
    pub commands: Capability,
    pub logs: Capability,
}

#[derive(Clone, Deserialize, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub(crate) enum Capability {
    Unsupported,
    Offline,
    Connected(String),
}

#[derive(Clone, Deserialize, Serialize)]
pub(crate) struct Target {
    pub id: ConsoleId,
    pub session: String,
}

#[derive(Clone, Copy, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "snake_case")]
pub(crate) enum CommandOutcome {
    Submitted,
    Offline,
    StaleSession,
    Busy,
    Expired,
    Unconfirmed,
}

impl CommandOutcome {
    pub(crate) fn description(self) -> &'static str {
        match self {
            Self::Submitted => "Command submitted to the console.",
            Self::Offline => "The console command connection is unavailable.",
            Self::StaleSession => {
                "The command connection changed. Select the current console session and submit again."
            }
            Self::Busy => "This console already has a command in progress.",
            Self::Expired => "The command expired before submission.",
            Self::Unconfirmed => "Result unconfirmed. Check the server before retrying.",
        }
    }
}

impl From<Failure> for CommandOutcome {
    fn from(failure: Failure) -> Self {
        match failure {
            Failure::Offline => Self::Offline,
            Failure::Busy => Self::Busy,
            Failure::Expired => Self::Expired,
            Failure::Unconfirmed => Self::Unconfirmed,
        }
    }
}

impl ProviderStatus {
    pub(crate) fn target(&self, lane: Lane) -> Option<Target> {
        let capability = match lane {
            Lane::Commands => &self.commands,
            Lane::Logs => &self.logs,
        };
        let Capability::Connected(session) = capability else {
            return None;
        };
        Some(Target {
            id: self.id.clone(),
            session: session.clone(),
        })
    }

    pub(crate) fn log_target(&self) -> io::Result<Target> {
        self.target(Lane::Logs).ok_or_else(|| match self.logs {
            Capability::Unsupported => io::Error::other("This console does not provide logs"),
            _ => io::Error::other("The console log connection is offline"),
        })
    }
}

impl Provider {
    fn new(startup: &str, registration: ConsoleRegistration) -> Self {
        Self {
            startup: startup.into(),
            registration,
            commands: None,
            logs: None,
            disconnected_at: Instant::now(),
        }
    }

    fn offline(&self) -> bool {
        self.commands.is_none() && self.logs.is_none()
    }
}

impl<R: Default> Connection<R> {
    fn open(slot: &mut Option<Self>, session: &str) -> io::Result<Arc<R>> {
        if slot.is_some() {
            return Err(io::Error::other("Console lane is already connected"));
        }
        let requests = Arc::new(R::default());
        *slot = Some(Self {
            session: session.into(),
            requests: requests.clone(),
        });
        Ok(requests)
    }
}

fn capability<R>(supported: bool, connection: Option<&Connection<R>>) -> Capability {
    match connection {
        Some(link) => Capability::Connected(link.session.clone()),
        None if supported => Capability::Offline,
        None => Capability::Unsupported,
    }
}

impl Console {
    fn providers(&self) -> MutexGuard<'_, BTreeMap<ConsoleId, Provider>> {
        self.providers
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
    }

    pub(crate) fn connect(
        &self,
        instance: &str,
        startup: &str,
        session: &str,
        lane: Lane,
        registration: &ConsoleRegistration,
    ) -> io::Result<Session<'_>> {
        if !valid_registration(registration, lane) {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "Invalid console registration",
            ));
        }
        let id = ConsoleId {
            instance: instance.into(),
            provider: registration.id.clone(),
        };
        let mut providers = self.providers();
        if !providers.contains_key(&id) && providers.len() == MAX_PROVIDERS {
            let oldest = providers
                .iter()
                .filter(|(_, state)| state.offline())
                .min_by_key(|(_, state)| state.disconnected_at)
                .map(|(id, _)| id.clone())
                .ok_or_else(|| io::Error::other("Console provider limit reached"))?;
            providers.remove(&oldest);
        }
        let state = providers
            .entry(id.clone())
            .or_insert_with(|| Provider::new(startup, registration.clone()));
        if state.startup != startup || state.registration != *registration {
            if !state.offline() {
                return Err(io::Error::other(
                    "Console registration differs from the active provider",
                ));
            }
            *state = Provider::new(startup, registration.clone());
        }
        let channel = match lane {
            Lane::Commands => Channel::Commands(Connection::open(&mut state.commands, session)?),
            Lane::Logs => Channel::Logs(Connection::open(&mut state.logs, session)?),
        };
        Ok(Session {
            console: self,
            id,
            channel,
        })
    }

    pub(crate) fn list(&self) -> Vec<ProviderStatus> {
        self.providers()
            .iter()
            .map(|(id, state)| ProviderStatus {
                id: id.clone(),
                label: state.registration.label.clone(),
                commands: capability(state.registration.accepts_input, state.commands.as_ref()),
                logs: capability(state.registration.offers_logs, state.logs.as_ref()),
            })
            .collect()
    }

    pub(crate) async fn logs(&self, target: Target, position: LogPosition) -> io::Result<LogPage> {
        let query = position.into_query()?;
        let requests = {
            let providers = self.providers();
            let Some(connection) = providers
                .get(&target.id)
                .and_then(|state| state.logs.as_ref())
            else {
                return Err(io::Error::other("The console log connection is offline"));
            };
            if connection.session != target.session {
                return Err(io::Error::other(
                    "The console log connection changed. Reload the latest log page.",
                ));
            }
            connection.requests.clone()
        };
        requests.submit(query).await.map_err(logs::request_error)?
    }

    pub(crate) async fn execute(&self, target: Target, line: String) -> io::Result<CommandOutcome> {
        if line.len() > MAX_COMMAND_BYTES
            || line.trim().is_empty()
            || line.chars().any(char::is_control)
        {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "Enter one console command of up to 4096 bytes.",
            ));
        }
        let requests = {
            let providers = self.providers();
            let Some(connection) = providers
                .get(&target.id)
                .and_then(|state| state.commands.as_ref())
            else {
                return Ok(CommandOutcome::Offline);
            };
            if connection.session != target.session {
                return Ok(CommandOutcome::StaleSession);
            }
            connection.requests.clone()
        };
        Ok(requests
            .submit(line)
            .await
            .unwrap_or_else(CommandOutcome::from))
    }
}

fn valid_registration(registration: &ConsoleRegistration, lane: Lane) -> bool {
    let supports_lane = match lane {
        Lane::Commands => registration.accepts_input,
        Lane::Logs => registration.offers_logs,
    };
    supports_lane
        && (1..=64).contains(&registration.id.len())
        && registration
            .id
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || b"._-".contains(&byte))
        && (1..=128).contains(&registration.label.len())
        && !registration.label.trim().is_empty()
        && !registration.label.chars().any(char::is_control)
}

impl Session<'_> {
    pub(crate) async fn poll_command(
        &self,
        receipt: Option<ConsoleCommandReceipt>,
        budget: Duration,
    ) -> io::Result<ConsoleCommandWork> {
        let Channel::Commands(requests) = &self.channel else {
            return Err(io::Error::other("Expected a console command connection"));
        };
        let receipt = receipt
            .map(|receipt| {
                let outcome = match Outcome::try_from(receipt.outcome) {
                    Ok(Outcome::Submitted) => CommandOutcome::Submitted,
                    Ok(Outcome::Expired) => CommandOutcome::Expired,
                    Ok(Outcome::Unconfirmed) => CommandOutcome::Unconfirmed,
                    _ => return Err(invalid_receipt()),
                };
                Ok((receipt.id, outcome))
            })
            .transpose()?;
        let command = requests
            .poll(receipt, budget)
            .await?
            .map(|delivery| ConsoleCommand {
                id: delivery.id,
                line: delivery.work,
                remaining_ms: delivery.remaining_ms,
            });
        Ok(ConsoleCommandWork { command })
    }

    pub(crate) async fn poll_logs(
        &self,
        receipt: Option<ConsoleLogReceipt>,
        budget: Duration,
    ) -> io::Result<ConsoleLogWork> {
        let Channel::Logs(requests) = &self.channel else {
            return Err(io::Error::other("Expected a console log connection"));
        };
        let receipt = receipt.map(logs::receipt).transpose()?;
        let read = requests
            .poll(receipt, budget)
            .await?
            .map(|delivery| ConsoleLogRead {
                id: delivery.id,
                query: Some(delivery.work),
                remaining_ms: delivery.remaining_ms,
            });
        Ok(ConsoleLogWork { read })
    }
}

impl Drop for Session<'_> {
    fn drop(&mut self) {
        let mut providers = self.console.providers();
        let Some(state) = providers.get_mut(&self.id) else {
            return;
        };
        match &self.channel {
            Channel::Commands(requests) => {
                requests.close();
                state.commands = None;
            }
            Channel::Logs(requests) => {
                requests.close();
                state.logs = None;
            }
        }
        if state.offline() {
            state.disconnected_at = Instant::now();
        }
    }
}
