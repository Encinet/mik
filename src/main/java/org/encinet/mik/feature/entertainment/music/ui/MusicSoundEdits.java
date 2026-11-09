package org.encinet.mik.feature.entertainment.music.ui;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxSoundSettings;

/** Main-thread adjustments: one write in flight, with the latest requested values retained for the next write. */
final class MusicSoundEdits implements AutoCloseable {
    enum Parameter {
        VOLUME("volume", JukeboxSoundSettings.MIN_VOLUME_PERCENT, JukeboxSoundSettings.MAX_VOLUME_PERCENT, 5),
        RANGE("range", JukeboxSoundSettings.MIN_RANGE_BLOCKS, JukeboxSoundSettings.MAX_RANGE_BLOCKS, JukeboxSoundSettings.RANGE_FINE_STEP),
        GAIN("gain", JukeboxSoundSettings.MIN_VOLUME_PERCENT, JukeboxSoundSettings.MAX_VOLUME_PERCENT, 5);

        final String key;
        final int minimum, maximum, step;
        Parameter(String key, int minimum, int maximum, int step) {
            this.key = key; this.minimum = minimum; this.maximum = maximum; this.step = step;
        }
        int parse(String raw) {
            int value = Integer.parseInt(raw.strip());
            if (value < minimum || value > maximum) throw new IllegalArgumentException("Sound value outside its range");
            return value;
        }
    }

    private final ToIntFunction<Parameter> current;
    private final Function<Map<Parameter, Integer>, CompletableFuture<Void>> save;
    private final Consumer<Runnable> dispatch;
    private final EnumMap<Parameter, Integer> desired = new EnumMap<>(Parameter.class);
    private CompletableFuture<Void> completion;
    private boolean closed;

    MusicSoundEdits(ToIntFunction<Parameter> current, Function<Map<Parameter, Integer>, CompletableFuture<Void>> save,
                    Consumer<Runnable> dispatch) {
        this.current = current; this.save = save; this.dispatch = dispatch;
    }

    int value(Parameter parameter) {
        var pending = desired.get(parameter);
        return pending == null ? current.applyAsInt(parameter) : pending;
    }
    boolean active() { return completion != null; }

    CompletableFuture<Void> change(Parameter parameter, int value) {
        if (closed) return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException());
        if (value < parameter.minimum || value > parameter.maximum) throw new IllegalArgumentException("Sound value outside its range");
        if (!active() && value == current.applyAsInt(parameter)) return CompletableFuture.completedFuture(null);
        desired.put(parameter, value);
        if (active()) return completion;
        var result = completion = new CompletableFuture<Void>();
        flush();
        return result;
    }

    private void flush() {
        var writing = Map.copyOf(desired);
        CompletableFuture<Void> future;
        try { future = save.apply(writing); }
        catch (Exception error) { finish(error); return; }
        future.whenComplete((_, error) -> dispatch.accept(() -> {
            if (closed) return;
            if (error != null) { finish(error); return; }
            writing.forEach((parameter, value) -> desired.remove(parameter, value));
            if (desired.isEmpty()) finish(null);
            else flush();
        }));
    }

    private void finish(Throwable error) {
        var result = completion;
        completion = null;
        desired.clear();
        if (error == null) result.complete(null);
        else result.completeExceptionally(error);
    }

    @Override public void close() {
        closed = true;
        desired.clear();
        if (completion != null) completion.cancel(false);
        completion = null;
    }
}
