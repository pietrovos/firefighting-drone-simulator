package DroneSwarmSim.model;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Timer class.
 */
public class TimerTest {

    @Test
    public void testOneShotTimer() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger counter = new AtomicInteger(0);

        Timer timer = new Timer(100, 0, TimeUnit.MILLISECONDS, t -> {
            counter.incrementAndGet();
            latch.countDown();
            t.stop();
        });

        timer.start();
        boolean completed = latch.await(500, TimeUnit.MILLISECONDS);

        assertTrue(completed, "Timer should have completed the task");
        assertEquals(1, counter.get(), "Task should have executed exactly once");
    }

    @Test
    public void testPeriodicTimer() throws InterruptedException {
        int expectedExecutions = 3;
        CountDownLatch latch = new CountDownLatch(expectedExecutions);
        AtomicInteger counter = new AtomicInteger(0);

        Timer timer = new Timer(50, 50, TimeUnit.MILLISECONDS, t -> {
            counter.incrementAndGet();
            latch.countDown();
            if (counter.get() >= expectedExecutions) {
                t.stop();
            }
        });

        timer.start();
        boolean completed = latch.await(1, TimeUnit.SECONDS);

        assertTrue(completed, "Periodic timer should have executed " + expectedExecutions + " times");
        assertTrue(counter.get() >= expectedExecutions, "Task should have executed at least " + expectedExecutions + " times");
    }

    @Test
    public void testDefaultConstructorAndRun() {
        Timer timer = new Timer();
        // Should not throw exception and should be runnable
        assertDoesNotThrow(timer::run);
        assertDoesNotThrow(timer::start);
        assertDoesNotThrow(timer::stop);
    }
}
