package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.MenuInputForm;

import java.util.LinkedHashMap;
import java.util.Map;

/** One form's unsaved values; accepting one field never commits or loses another field. */
final class MenuInputDraft {
    private final MenuInputForm form;
    private final Map<String, String> values = new LinkedHashMap<>();
    private int selected;
    private int viewed;
    private boolean submitted;
    private Problem rejected;
    record Problem(MenuInputForm.Field field, long length, boolean singleLine) { }

    MenuInputDraft(MenuInputForm form) {
        this.form = form;
        for (var field : form.fields()) values.put(field.key(), field.initial());
    }
    int selected() { return selected; }
    int viewed() { return viewed; }
    MenuInputForm.Field field() { return form.fields().get(selected); }
    String value(String key) { return values.get(key); }
    boolean submitted() { return submitted; }
    void select(int index) {
        if (index < 0 || index >= form.fields().size()) throw new IndexOutOfBoundsException(index);
        selected = index;
        viewed = index;
        rejected = null;
    }
    void browse(int index) {
        if (index < 0 || index >= form.fields().size()) throw new IndexOutOfBoundsException(index);
        if (!submitted) viewed = index;
    }
    static Problem validate(MenuInputForm.Field field, String value) {
        long length = value.codePointCount(0, value.length());
        boolean singleLine = !field.multiline() && (value.contains("\n") || value.contains("\r"));
        return length > field.maximumLength() || singleLine ? new Problem(field, length, singleLine) : null;
    }
    void receive(String raw) {
        if (submitted) return;
        rejected = validate(field(), raw);
        if (rejected == null) values.put(field().key(), raw);
    }
    void clear() { clear(selected); }
    void clear(int index) {
        if (index < 0 || index >= form.fields().size()) throw new IndexOutOfBoundsException(index);
        if (submitted) return;
        var target = form.fields().get(index);
        values.put(target.key(), "");
        if (rejected != null && rejected.field().key().equals(target.key())) rejected = null;
    }
    Problem problem() {
        if (rejected != null) return rejected;
        for (var field : form.fields()) {
            var problem = validate(field, value(field.key()));
            if (problem != null) return problem;
        }
        return null;
    }
    MenuInputForm.Values values() { return new MenuInputForm.Values(values); }
    boolean submit() {
        if (submitted || problem() != null) return false;
        submitted = true;
        return true;
    }
}
