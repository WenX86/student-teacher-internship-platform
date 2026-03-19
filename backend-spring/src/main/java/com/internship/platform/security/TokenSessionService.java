package com.internship.platform.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenSessionService {

    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    public String createToken(String userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, userId);
        return token;
    }

    public String resolveUserId(String token) {
        return sessions.get(token);
    }

    public void remove(String token) {
        sessions.remove(token);
    }
}
