use std::{
    fs::{self, File, OpenOptions},
    io,
    os::unix::{
        fs::{FileTypeExt, MetadataExt, OpenOptionsExt, PermissionsExt},
        net::UnixStream as BlockingStream,
    },
    path::{Path, PathBuf},
};
use tokio::net::{UnixListener, UnixStream};

pub(crate) struct SocketListener {
    listener: UnixListener,
    peer: Peer,
    _owner: SocketOwner,
}

enum Peer {
    Paper(u32),
    Admin(u32),
}

impl Peer {
    fn allows(&self, uid: u32) -> bool {
        match *self {
            Self::Paper(game) => uid == game,
            Self::Admin(owner) => uid == 0 || uid == owner,
        }
    }
}

struct SocketOwner {
    path: PathBuf,
    device: u64,
    inode: u64,
    _lock: File,
}

impl SocketListener {
    pub(crate) fn bind(path: &Path, game_uid: u32) -> io::Result<Self> {
        Self::bind_with(path, Some(game_uid))
    }

    pub(crate) fn bind_admin(path: &Path) -> io::Result<Self> {
        Self::bind_with(path, None)
    }

    fn bind_with(path: &Path, game_uid: Option<u32>) -> io::Result<Self> {
        let parent = path
            .parent()
            .ok_or_else(|| invalid("socket parent missing"))?;
        let metadata = fs::symlink_metadata(parent)?;
        if !metadata.is_dir() || metadata.permissions().mode() & 0o027 != 0 {
            return Err(invalid(
                "socket directory must deny other access and group writes",
            ));
        }
        let owner_uid = metadata.uid();
        let lock_path = path.with_extension("lock");
        if let Ok(lock) = fs::symlink_metadata(&lock_path)
            && (!lock.is_file() || lock.uid() != metadata.uid())
        {
            return Err(invalid("unsafe socket lock"));
        }
        let lock = OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .truncate(false)
            .mode(0o600)
            .open(lock_path)?;
        lock.try_lock().map_err(io::Error::other)?;
        if let Ok(old) = fs::symlink_metadata(path) {
            if !old.file_type().is_socket() || old.uid() != metadata.uid() {
                return Err(invalid("refusing to replace foreign socket path"));
            }
            match BlockingStream::connect(path) {
                Err(error) if error.kind() == io::ErrorKind::ConnectionRefused => {
                    fs::remove_file(path)?;
                }
                _ => return Err(io::Error::new(io::ErrorKind::AddrInUse, "socket is active")),
            }
        }
        let listener = UnixListener::bind(path)?;
        let metadata = fs::symlink_metadata(path)?;
        let owner = SocketOwner {
            path: path.to_owned(),
            device: metadata.dev(),
            inode: metadata.ino(),
            _lock: lock,
        };
        let (peer, mode) = game_uid.map_or((Peer::Admin(owner_uid), 0o600), |uid| {
            (Peer::Paper(uid), 0o660)
        });
        fs::set_permissions(path, fs::Permissions::from_mode(mode))?;
        Ok(Self {
            listener,
            peer,
            _owner: owner,
        })
    }

    pub(super) async fn accept(&self) -> io::Result<UnixStream> {
        loop {
            let (stream, _) = self.listener.accept().await?;
            if stream
                .peer_cred()
                .is_ok_and(|peer| self.peer.allows(peer.uid()))
            {
                return Ok(stream);
            }
        }
    }
}

impl Drop for SocketOwner {
    fn drop(&mut self) {
        if let Ok(metadata) = fs::symlink_metadata(&self.path)
            && metadata.dev() == self.device
            && metadata.ino() == self.inode
        {
            // Best-effort unlink; the next listener also cleans up stale sockets under the lock.
            drop(fs::remove_file(&self.path));
        }
    }
}

pub(super) fn invalid(error: impl Into<Box<dyn std::error::Error + Send + Sync>>) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, error)
}

#[cfg(test)]
mod tests {
    use super::Peer;

    #[test]
    fn admin_does_not_inherit_paper_peer_permissions() {
        let daemon = 1001;
        let game = 1002;
        assert!(Peer::Admin(daemon).allows(daemon));
        assert!(Peer::Admin(daemon).allows(0));
        assert!(!Peer::Admin(daemon).allows(game));
        assert!(Peer::Paper(game).allows(game));
        assert!(!Peer::Paper(game).allows(daemon));
    }
}
