package org.fentanylsolutions.wawelauth.wawelserver;

import org.junit.Assert;
import org.junit.Test;

public class RequestRateLimiterTest {

    @Test
    public void allowsRequestsUntilLimitThenReturnsRetryAfter() {
        RequestRateLimiter limiter = new RequestRateLimiter(10);

        Assert.assertEquals(0L, limiter.tryAcquire("ip:1", 2, 1000L, 1000L));
        Assert.assertEquals(0L, limiter.tryAcquire("ip:1", 2, 1000L, 1001L));
        Assert.assertTrue(limiter.tryAcquire("ip:1", 2, 1000L, 1002L) > 0L);
    }

    @Test
    public void resetsAfterWindowExpires() {
        RequestRateLimiter limiter = new RequestRateLimiter(10);

        Assert.assertEquals(0L, limiter.tryAcquire("ip:1", 1, 1000L, 1000L));
        Assert.assertTrue(limiter.tryAcquire("ip:1", 1, 1000L, 1001L) > 0L);
        Assert.assertEquals(0L, limiter.tryAcquire("ip:1", 1, 1000L, 2000L));
    }

    @Test
    public void failureCheckDoesNotConsumeBudgetUntilRecorded() {
        RequestRateLimiter limiter = new RequestRateLimiter(10);

        Assert.assertEquals(0L, limiter.checkLimit("account:test", 1, 1000L, 1000L));
        Assert.assertEquals(0L, limiter.checkLimit("account:test", 1, 1000L, 1001L));
        limiter.recordFailure(true, "account:test", 1);

        Assert.assertTrue(limiter.checkLimit("account:test", 1, 1000L, System.currentTimeMillis()) > 0L);
    }

    @Test
    public void resetClearsBucket() {
        RequestRateLimiter limiter = new RequestRateLimiter(10);

        Assert.assertEquals(0L, limiter.tryAcquire("ip:1", 1, 1000L, 1000L));
        Assert.assertTrue(limiter.tryAcquire("ip:1", 1, 1000L, 1001L) > 0L);
        limiter.reset("ip:1");
        Assert.assertEquals(0L, limiter.tryAcquire("ip:1", 1, 1000L, 1002L));
    }

    @Test
    public void keyPartNormalizesAndHashesUnsafeValues() {
        Assert.assertEquals("alice", RequestRateLimiter.keyPart(" Alice "));
        Assert.assertTrue(
            RequestRateLimiter.keyPart("token|with|separators")
                .startsWith("sha256:"));
    }
}
