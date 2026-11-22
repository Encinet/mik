pub(crate) mod admin;
mod control;
mod music;
mod socket;

pub(crate) use control::{PaperServices, serve as serve_control};
pub(crate) use music::serve as serve_music;
pub(crate) use socket::SocketListener;
