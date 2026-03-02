package aamscool.backend.aamschoolbackend.security;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
public class JwtTokenBlacklistService {

    private final Cache<String, Boolean> blacklist;

    public JwtTokenBlacklistService(@Value("${app.security.jwt.expiration-minutes:120}") long expirationMinutes) {
        this.blacklist = Caffeine.newBuilder()
                .expireAfterWrite(expirationMinutes, TimeUnit.MINUTES)
                .maximumSize(100_000)
                .build();
    }

    public void blacklist(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        blacklist.put(token, Boolean.TRUE);
    }

    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return blacklist.getIfPresent(token) != null;
    }
}
