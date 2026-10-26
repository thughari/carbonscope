package com.carbonscope.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class EmissionCacheService {

    private record CacheEntry(Object data, long expireAtMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() > expireAtMillis;
        }
    }

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    private static final long DEFAULT_TTL_MS = 2 * 60 * 60 * 1000L; // 2 hours

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }
        if (entry.isExpired()) {
            cache.remove(key);
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return (T) entry.data();
    }

    public <T> void put(String key, T value) {
        put(key, value, DEFAULT_TTL_MS);
    }

    public <T> void put(String key, T value, long ttlMs) {
        if (value == null) return;
        long expireAt = System.currentTimeMillis() + ttlMs;
        cache.put(key, new CacheEntry(value, expireAt));
    }

    public boolean contains(String key) {
        CacheEntry entry = cache.get(key);
        return entry != null && !entry.isExpired();
    }

    public void clear() {
        cache.clear();
    }

    public Map<String, Object> getCacheStats() {
        return Map.of(
            "size", cache.size(),
            "hits", hits.get(),
            "misses", misses.get(),
            "hitRate", (hits.get() + misses.get() > 0) ? String.format("%.1f%%", (double) hits.get() / (hits.get() + misses.get()) * 100) : "0%",
            "lastChecked", Instant.now().toString()
        );
    }
}
