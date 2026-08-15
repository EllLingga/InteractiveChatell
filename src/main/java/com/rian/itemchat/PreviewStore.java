package com.rian.itemchat;

import com.rian.itemchat.model.PreviewData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PreviewStore {

    private static final String CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final long EXPIRE_MILLIS = 10 * 60 * 1000L; // 10 minutes

    private final Map<String, PreviewData> store = new ConcurrentHashMap<>();

    public String save(PreviewData data) {
        String token;
        do {
            token = randomToken(6);
        } while (store.containsKey(token));
        store.put(token, data);
        return token;
    }

    public PreviewData get(String token) {
        return store.get(token);
    }

    /** Removes entries older than the expiry window. Call this periodically. */
    public void cleanup() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(entry -> now - entry.getValue().getCreatedAt() > EXPIRE_MILLIS);
    }

    private String randomToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
