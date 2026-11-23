use super::WikiEdition;
use rustix::fs::{Mode, OFlags, open};
use std::{
    fs::{self, DirBuilder, File},
    io,
    os::unix::fs::{DirBuilderExt, PermissionsExt},
    path::{Path, PathBuf},
    sync::OnceLock,
    time::{Duration, SystemTime},
};

pub(super) struct DiskCache {
    root: PathBuf,
    ownership: OnceLock<Option<File>>,
}

impl DiskCache {
    pub(super) const fn new(root: PathBuf) -> Self {
        Self {
            root,
            ownership: OnceLock::new(),
        }
    }

    pub(super) fn path(&self, edition: WikiEdition) -> Option<PathBuf> {
        self.ownership
            .get_or_init(|| match self.lock() {
                Ok(owner) => Some(owner),
                Err(error) => {
                    eprintln!("mikd Minecraft Wiki disk cache disabled: {error}");
                    None
                }
            })
            .as_ref()?;
        Some(self.root.join(format!("v1-{}", edition.code())))
    }

    fn lock(&self) -> io::Result<File> {
        if !self.root.is_absolute() {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "Wiki cache path must be absolute",
            ));
        }
        ensure_private_dir(&self.root)?;
        let file = File::from(open(
            self.root.join(".owner"),
            OFlags::RDWR | OFlags::CREATE | OFlags::NOFOLLOW | OFlags::NONBLOCK | OFlags::CLOEXEC,
            Mode::RUSR | Mode::WUSR,
        )?);
        if !file.metadata()?.is_file() {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "invalid Wiki cache lock",
            ));
        }
        file.try_lock().map_err(io::Error::other)?;
        Ok(file)
    }
}

pub(super) fn ensure_private_dir(path: &Path) -> io::Result<()> {
    match DirBuilder::new().mode(0o700).create(path) {
        Ok(()) => (),
        Err(error) if error.kind() == io::ErrorKind::AlreadyExists => (),
        Err(error) => return Err(error),
    }
    check_private_dir(path)
}

fn check_private_dir(path: &Path) -> io::Result<()> {
    let dir = fs::symlink_metadata(path)?;
    if !dir.is_dir() || dir.permissions().mode() & 0o077 != 0 {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "Wiki cache directory must be private",
        ));
    }
    Ok(())
}

fn metadata_file(path: &Path) -> io::Result<File> {
    check_private_dir(path)?;
    let file = File::from(open(
        path.join("meta.json"),
        OFlags::RDONLY | OFlags::NOFOLLOW | OFlags::NONBLOCK | OFlags::CLOEXEC,
        Mode::empty(),
    )?);
    let metadata = file.metadata()?;
    if !metadata.is_file() || metadata.len() > 1024 * 1024 {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "invalid Wiki cache manifest",
        ));
    }
    Ok(file)
}

pub(super) fn age(path: &Path) -> io::Result<Duration> {
    let modified = metadata_file(path)?.metadata()?.modified()?;
    SystemTime::now()
        .duration_since(modified)
        .map_err(io::Error::other)
}

pub(super) fn revalidate(path: &Path) -> io::Result<()> {
    metadata_file(path)?.set_modified(SystemTime::now())
}
