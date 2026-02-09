package aamscool.backend.aamschoolbackend.config;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

@Component
public class ScraperLock {

    private final AtomicBoolean running = new AtomicBoolean(false);

    public boolean tryLock() {
        return running.compareAndSet(false, true);
    }

    public void unlock() {
        running.set(false);
    }
}

