package org.encinet.mik.feature.entertainment.music.rhythm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Main-thread lobby state; changing preferences never starts audio or changes another player. */
public final class RhythmRoom {
    public record Member(
            UUID player, RhythmGameMode mode, RhythmDifficulty difficulty, boolean ready, boolean playing) {}
    private final UUID device, controller;
    private final Map<UUID, Member> members = new LinkedHashMap<>();
    public RhythmRoom(UUID device, UUID controller) {
        this.device = device;
        this.controller = controller;
        join(controller);
    }
    public UUID device() {
        return device;
    }
    public UUID controller() {
        return controller;
    }
    public List<Member> members() {
        return List.copyOf(members.values());
    }
    public Member member(UUID player) {
        return members.get(player);
    }
    public void join(UUID player) {
        members.putIfAbsent(
                player, new Member(player, RhythmGameMode.FALLING, RhythmDifficulty.NORMAL, false, false));
    }
    public void preference(UUID player, RhythmGameMode mode, RhythmDifficulty difficulty) {
        Member m = Objects.requireNonNull(members.get(player));
        if (m.playing())
            throw new IllegalStateException("Playing");
        members.put(player, new Member(player, mode, difficulty, false, false));
    }
    public void ready(UUID player, boolean value) {
        Member m = Objects.requireNonNull(members.get(player));
        if (m.playing())
            throw new IllegalStateException("Playing");
        members.put(player, new Member(player, m.mode(), m.difficulty(), value, false));
    }
    public List<Member> start(UUID actor) {
        if (!controller.equals(actor))
            throw new SecurityException("Controller required");
        List<Member> ready = members().stream().filter(m -> m.ready() && !m.playing()).toList();
        for (Member m : ready)
            members.put(m.player(), new Member(m.player(), m.mode(), m.difficulty(), false, true));
        return ready;
    }
    public void markPlaying(UUID player) {
        Member m = Objects.requireNonNull(members.get(player));
        members.put(player, new Member(player, m.mode(), m.difficulty(), false, true));
    }
    public void finish(UUID player) {
        Member m = members.get(player);
        if (m != null)
            members.put(player, new Member(player, m.mode(), m.difficulty(), false, false));
    }
    public void leave(UUID player) {
        members.remove(player);
    }
    public void changedTrack() {
        members.replaceAll((id, m) -> new Member(id, m.mode(), m.difficulty(), false, false));
    }
}
