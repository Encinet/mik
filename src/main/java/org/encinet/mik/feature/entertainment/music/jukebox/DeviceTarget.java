package org.encinet.mik.feature.entertainment.music.jukebox;
import java.util.List;
import java.util.UUID;
public record DeviceTarget(UUID id, String name, List<MusicDevice> members) {
    public DeviceTarget {
        members = List.copyOf(members);
    }
}
