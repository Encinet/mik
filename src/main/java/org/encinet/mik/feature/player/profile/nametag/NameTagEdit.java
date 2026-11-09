package org.encinet.mik.feature.player.profile.nametag;

import net.luckperms.api.node.Node;
import org.encinet.mik.feature.player.profile.PlayerNameTag;

import java.util.Map;
import java.util.Set;

/** One field draft; only feature-owned nodes form its write baseline. */
final class NameTagEdit {
    private final NameTagField field;
    private String value;
    private Set<Node> baseline;
    private PlayerNameTag original;
    private NameTagFormat.Failure failure;
    private boolean saving;

    NameTagEdit(NameTagField field, NameTagStore.Snapshot custom, PlayerNameTag effective) {
        this.field = field;
        value = field.value(effective);
        rebase(custom, effective);
    }

    NameTagField field() { return field; }
    String value(NameTagField field) {
        if (this.field != field) throw new IllegalArgumentException("Field is not part of this draft");
        return value;
    }
    NameTagFormat.Failure failure() { return failure; }
    boolean valid() { return failure == null; }
    boolean saving() { return saving; }
    boolean removesCustomValue() { return value.isEmpty() && !baseline.isEmpty(); }
    boolean canSave() {
        return !saving && valid() && (value.isEmpty() ? !baseline.isEmpty() : !value.equals(field.value(original)));
    }

    Map<NameTagField, NameTagStore.Change> changes() {
        return Map.of(field, new NameTagStore.Change(baseline, value));
    }

    PlayerNameTag preview(PlayerNameTag effective) { return field.replace(effective, value); }

    void input(String raw) {
        if (saving) return;
        failure = NameTagFormat.validate(raw).orElse(null);
        if (failure == null) value = raw;
    }

    boolean beginSave() {
        if (!canSave()) return false;
        saving = true;
        return true;
    }

    void failed() { saving = false; }

    void rebase(NameTagStore.Snapshot custom, PlayerNameTag effective) {
        baseline = custom.version(field);
        original = effective;
        saving = false;
    }
}
