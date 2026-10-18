package com.maxwai.nclientv3.utility;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class RateLimitStore {
    public static final RateLimitStore SHARED = new RateLimitStore(System::currentTimeMillis);
    private final ConcurrentHashMap<String, Long> deadlines = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    public RateLimitStore(LongSupplier clock) { this.clock = clock; }
    public long remainingSeconds(String scope) {
        long remaining = deadlines.getOrDefault(scope, 0L) - clock.getAsLong();
        return remaining <= 0 ? 0 : (remaining + 999) / 1000;
    }
    public void record(String scope, String retryAfter) {
        long now = clock.getAsLong(), delay = 30_000;
        if (retryAfter != null) {
            try { delay = Math.multiplyExact(Long.parseLong(retryAfter.trim()), 1000L); }
            catch (NumberFormatException | ArithmeticException numberError) {
                try { delay = ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now; }
                catch (java.time.DateTimeException dateError) { delay = 30_000; }
            }
        }
        delay = Math.max(1000, delay);
        long until = now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay;
        deadlines.merge(scope, until, Math::max);
    }
}
