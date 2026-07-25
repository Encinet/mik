package org.encinet.mik.module.music.jukebox;

import org.encinet.mik.module.music.catalog.TrackTarget;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Validates trusted local media paths immediately before a playback engine opens them. */
final class LocalMediaPreparer {

    Path prepare(TrackTarget.LocalFile target) throws IOException {
        return prepare(target.path(), target.root());
    }

    Path prepare(TrackTarget.NbsFile target) throws IOException {
        return prepare(target.path(), target.root());
    }

    private Path prepare(Path path, Path root) throws IOException {
        if (!path.startsWith(root) || Files.isSymbolicLink(root)) {
            throw new IOException("Local music path is outside its trusted root");
        }
        Path current = root;
        for (Path component : root.relativize(path)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Local music path contains a symbolic link");
            }
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Local music file is unavailable");
        }
        return path;
    }
}
