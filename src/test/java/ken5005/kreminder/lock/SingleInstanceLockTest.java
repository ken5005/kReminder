package ken5005.kreminder.lock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleInstanceLockTest {

    private final ContentionHandler neverCalledHandler = new ContentionHandler() {
        @Override
        public Choice onExistingInstance(InstanceInfo holder) {
            throw new AssertionError("競合しない状況で呼ばれてはならない");
        }

        @Override
        public Fallback onNoResponse(InstanceInfo holder) {
            throw new AssertionError("競合しない状況で呼ばれてはならない");
        }
    };

    @Test
    void acquireOnEmptyDirSucceedsAndWritesFiles(@TempDir Path base) {
        SingleInstanceLock lock = new SingleInstanceLock(base, msg -> { }, 0);

        AcquireResult result = lock.acquire(neverCalledHandler);

        assertEquals(AcquireResult.ACQUIRED, result);
        assertTrue(Files.exists(base.resolve(".instance.lock")));
        assertTrue(Files.exists(base.resolve(".instance.info")));

        lock.release();
    }

    @Test
    void releaseCleansUpInfoAndStopRequestButKeepsLockFile(@TempDir Path base) {
        SingleInstanceLock lock = new SingleInstanceLock(base, msg -> { }, 0);
        lock.acquire(neverCalledHandler);

        lock.release();

        assertTrue(Files.exists(base.resolve(".instance.lock")));
        assertFalse(Files.exists(base.resolve(".instance.info")));
    }

    @Test
    void negativeStopTimeoutIsRejected(@TempDir Path base) {
        assertThrows(IllegalArgumentException.class,
                () -> new SingleInstanceLock(base, msg -> { }, -1));
    }

    @Test
    void zeroStopTimeoutIsAccepted(@TempDir Path base) {
        SingleInstanceLock lock = new SingleInstanceLock(base, msg -> { }, 0);
        AcquireResult result = lock.acquire(neverCalledHandler);
        assertEquals(AcquireResult.ACQUIRED, result);
        lock.release();
    }
}
