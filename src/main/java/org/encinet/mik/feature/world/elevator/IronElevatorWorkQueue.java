package org.encinet.mik.feature.world.elevator;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Cooperative primary-thread work with a global budget and bounded service for every priority. */
final class IronElevatorWorkQueue {
    static final int INPUT = 0;
    static final int HINT = 1;
    static final int CATALOG = 2;
    private static final int MAX_TASKS = 256;
    private static final int MAX_STEPS = 256;
    private static final long TICK_BUDGET_NANOS = 1_000_000;
    private static final int[] SERVICE_ORDER = {INPUT, INPUT, INPUT, INPUT, HINT, HINT, CATALOG};

    final class Task {
        private final BooleanSupplier work;
        private int priority;
        private boolean finished;

        private Task(int priority, BooleanSupplier work) {
            this.priority = priority;
            this.work = work;
        }

        void cancel() {
            finished = true;
            if (tasks.get(priority).remove(this)) size--;
        }

        void priority(int priority) {
            if (finished || this.priority == priority) return;
            boolean queued = tasks.get(this.priority).remove(this);
            this.priority = priority;
            if (queued) tasks.get(priority).addLast(this);
        }
    }

    private final List<ArrayDeque<Task>> tasks = List.of(new ArrayDeque<>(), new ArrayDeque<>(), new ArrayDeque<>());
    private final LongSupplier clock;
    private final Consumer<RuntimeException> failure;
    private int size;
    private int service;
    private Task running;

    IronElevatorWorkQueue(Consumer<RuntimeException> failure) {
        this(System::nanoTime, failure);
    }

    IronElevatorWorkQueue(LongSupplier clock, Consumer<RuntimeException> failure) {
        this.clock = clock;
        this.failure = failure;
    }

    Task submit(int priority, BooleanSupplier work) {
        if (size >= MAX_TASKS || priority != INPUT && size >= MAX_TASKS - MAX_TASKS / 8
                || priority == CATALOG && tasks.get(CATALOG).size() >= MAX_TASKS - MAX_TASKS / 4) return null;
        Task task = new Task(priority, work);
        tasks.get(priority).addLast(task);
        size++;
        return task;
    }

    boolean empty() { return size == 0; }

    private Task take() {
        int priority = SERVICE_ORDER[service];
        service = (service + 1) % SERVICE_ORDER.length;
        if (tasks.get(priority).isEmpty()) {
            for (int candidate = INPUT; candidate <= CATALOG; candidate++) {
                if (!tasks.get(candidate).isEmpty()) {
                    priority = candidate;
                    break;
                }
            }
        }
        size--;
        return tasks.get(priority).removeFirst();
    }

    void tick() {
        long start = clock.getAsLong();
        for (int step = 0; step < MAX_STEPS && size > 0; step++) {
            if (step > 0 && clock.getAsLong() - start >= TICK_BUDGET_NANOS) break;
            Task task = take();
            running = task;
            try {
                if (task.work.getAsBoolean()) task.finished = true;
                if (!task.finished) {
                    tasks.get(task.priority).addLast(task);
                    size++;
                }
            } catch (RuntimeException error) {
                task.finished = true;
                failure.accept(error);
            } finally {
                running = null;
            }
        }
    }

    void clear() {
        if (running != null) running.finished = true;
        tasks.forEach(queue -> {
            queue.forEach(task -> task.finished = true);
            queue.clear();
        });
        size = 0;
        service = 0;
    }
}
