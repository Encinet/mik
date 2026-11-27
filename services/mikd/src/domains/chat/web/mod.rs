mod application;
mod live;
mod record;
mod render;
mod renewal;
mod session;
#[cfg(test)]
mod test_support;
mod transport;
pub(crate) use session::{Grant, Outcome, WebChat};
pub(crate) use transport::Identity;

#[derive(Clone, Copy, PartialEq, Eq)]
enum Lane {
    Live,
    History,
}
