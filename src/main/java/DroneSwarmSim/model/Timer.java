package DroneSwarmSim.model;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Represents a Timer that executes a specific task in a separate thread.
 * Implements the Runnable interface to allow execution within a thread.
 * <p>
 * The Timer class can be used to perform periodic or time-dependent operations
 * in the DroneSwarmSim system. The specific behaviour of the timer is defined
 * within the run method when overridden or extended.
 */
public class Timer implements Runnable {

    private final ScheduledExecutorService scheduler;
    private final long delay;
    private final long period;
    private final TimeUnit unit;
    private final Consumer<Timer> task;
    private boolean running;

    /**
     * Default constructor for the Timer class.
     * Initializes a new instance of the Timer, which can be used for executing
     * tasks in a separate thread within the DroneSwarmSim system context.
     * The Timer is intended for time-dependent or periodic operations.
     * <p>
     * Specific behaviour or initialization logic, if required, should be implemented
     * in subclasses or by extending the functionality provided by the Timer.
     */
    public Timer() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TimerThread");
            t.setDaemon(true);
            return t;
        });
        this.delay = 0;
        this.period = 0;
        this.unit = TimeUnit.MILLISECONDS;
        this.task = null;
        this.running = false;
    }

    /**
     * Constructs a Timer with a specific delay, period, and task.
     *
     * @param delay  Initial delay before the first execution.
     * @param period Period between successive executions. If 0, the task runs only once.
     * @param unit   The time unit for delay and period.
     * @param task   The task to execute, receiving this timer instance as an argument.
     */
    public Timer(long delay, long period, TimeUnit unit, Consumer<Timer> task) {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TimerThread-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        this.delay = delay;
        this.period = period;
        this.unit = unit;
        this.task = task;
        this.running = false;
    }

    /**
     * Starts the timer by scheduling the associated task for execution.
     * <p>
     * This method ensures that the timer is only started if it is not already running
     * and if a task has been defined. If the `period` is greater than 0, the task will
     * be scheduled to execute periodically at the specified time interval. Otherwise,
     * the task will be scheduled to execute once after the specified delay.
     * <p>
     * The timer uses a scheduler to manage the execution of tasks. The specific
     * timing is controlled by the `delay`, `period`, and `unit` parameters provided
     * during the timer's initialization.
     * <p>
     * If the timer is already running or the task is null, this method will return
     * without scheduling any tasks.
     */
    public synchronized void start() {
        if (running || task == null) return;
        running = true;
        if (period > 0) { scheduler.scheduleAtFixedRate(this, delay, period, unit); }
        else { scheduler.schedule(this, delay, unit); }
    }

    /**
     * Stops the currently running timer.
     * <p>
     * This method halts the execution of the timer by setting the running
     * flag to {@code false} and immediately shuts down the scheduler associated
     * with the timer. Once stopped, the timer cannot be restarted without
     * creating a new instance or invoking the required initialization methods.
     * <p>
     * It ensures that any ongoing or future scheduled tasks are cancelled
     * to avoid unintended behaviour.
     */
    public synchronized void stop() {
        running = false;
        scheduler.shutdownNow();
    }

    /**
     * Executes a task in a separate thread when invoked.
     * The specific logic or functionality to be performed by this method
     * should be defined by overriding this method in subclasses or through
     * an implementation of the Runnable interface.
     * <p>
     * This method is typically used to implement time-sensitive or periodic
     * actions within the DroneSwarmSim system.
     */
    @Override
    public void run() { if (task != null) task.accept(this); }
}
