package org.encinet.mik.feature.world.elevator;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Cooperative primary-thread work. Budgets are global, rather than per player or shaft. */
final class IronElevatorWorkQueue {
    static final int INPUT = 0;
    static final int HINT = 1;
    static final int CATALOG = 2;
    private static final int MAX_TASKS = 256;
    private static final int MAX_STEPS = 256;
    private static final long TICK_BUDGET_NANOS = 1_000_000;

    final class Task {
        private final BooleanSupplier work;
        private int priority;
        private long order;
        private boolean cancelled;

        private Task(int priority, BooleanSupplier work) {
            this.priority = priority;
            this.work = work;
            order = sequence++;
        }

        void cancel() {
            cancelled = true;
            tasks.remove(this);
        }

        void prioritizeInput() {
            if (cancelled || priority == INPUT) return;
            if (tasks.remove(this)) {
                priority = INPUT;
                tasks.add(this);
            }
        }
    }

    private final PriorityQueue<Task> tasks = new PriorityQueue<>(Comparator
            .comparingInt((Task task) -> task.priority).thenComparingLong(task -> task.order));
    private final LongSupplier clock;
    private final Consumer<RuntimeException> failure;
    private long sequence;

    IronElevatorWorkQueue(Consumer<RuntimeException> failure) {
        this(System::nanoTime, failure);
    }

    IronElevatorWorkQueue(LongSupplier clock, Consumer<RuntimeException> failure) {
        this.clock = clock;
        this.failure = failure;
    }

    Task submit(int priority, BooleanSupplier work) {
        if (tasks.size() >= MAX_TASKS) return null;
        Task task = new Task(priority, work);
        tasks.add(task);
        return task;
    }

    boolean empty() { return tasks.isEmpty(); }

    void tick() {
        long start = clock.getAsLong();
        for (int step = 0; step < MAX_STEPS && !tasks.isEmpty(); step++) {
            if (step > 0 && clock.getAsLong() - start >= TICK_BUDGET_NANOS) break;
            Task task = tasks.remove();
            if (task.cancelled) continue;
            try {
                if (!task.work.getAsBoolean() && !task.cancelled) {
                    task.order = sequence++;
                    tasks.add(task);
                }
            } catch (RuntimeException error) {
                task.cancelled = true;
                failure.accept(error);
            }
        }
    }

    void clear() {
        tasks.forEach(task -> task.cancelled = true);
        tasks.clear();
    }
}
