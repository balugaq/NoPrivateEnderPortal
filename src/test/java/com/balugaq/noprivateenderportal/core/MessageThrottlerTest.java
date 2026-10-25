package com.balugaq.noprivateenderportal.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MessageThrottler} 冷却逻辑测试。
 */
class MessageThrottlerTest {

    private static final UUID ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void zeroCooldownAlwaysAllows() {
        MessageThrottler throttler = new MessageThrottler();
        assertTrue(throttler.tryAcquire(ID, 0));
        assertTrue(throttler.tryAcquire(ID, 0));
        assertTrue(throttler.tryAcquire(ID, -1));
    }

    @Test
    void secondCallWithinCooldownIsDenied() {
        MessageThrottler throttler = new MessageThrottler();
        assertTrue(throttler.tryAcquire(ID, 1000));
        assertFalse(throttler.tryAcquire(ID, 1000));
    }

    @Test
    void callAfterCooldownElapsesIsAllowed() throws InterruptedException {
        MessageThrottler throttler = new MessageThrottler();
        assertTrue(throttler.tryAcquire(ID, 100));
        assertFalse(throttler.tryAcquire(ID, 100));

        TimeUnit.MILLISECONDS.sleep(150);
        assertTrue(throttler.tryAcquire(ID, 100));
    }

    @Test
    void forgetClearsCooldown() {
        MessageThrottler throttler = new MessageThrottler();
        assertTrue(throttler.tryAcquire(ID, 1000));
        throttler.forget(ID);
        // 清理后应视为首次触发，允许发送
        assertTrue(throttler.tryAcquire(ID, 1000));
    }
}
