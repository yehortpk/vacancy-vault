package com.github.yehortpk.parser.scrapper.browser;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-size pool of {@link BrowserInstance}s, borrowed exclusively and returned after every page.
 * <p>
 * Chromium dominates the parser's memory, so the pool size is the ceiling for the whole process:
 * a page cannot be rendered without holding an instance, and an instance is only released once its
 * page is done. Browsers, contexts and pages are all capped at the pool size as a result, which is
 * what makes a single knob enough to fit the parser into a constrained host.
 * <p>
 * Instances are launched on first use rather than upfront, so a run that touches no dynamic site
 * never pays for a browser.
 */
@Slf4j
@Component
public class BrowserPool {
    private final BlockingQueue<BrowserInstance> availableInstances;
    private final AtomicInteger launchedInstancesCnt = new AtomicInteger();
    private final int poolSize;
    private final int borrowTimeoutSec;

    public BrowserPool(
            @Value("${parser.browser.pool-size}") int poolSize,
            @Value("${parser.browser.borrow-timeout-sec}") int borrowTimeoutSec
    ) {
        this.poolSize = poolSize;
        this.borrowTimeoutSec = borrowTimeoutSec;
        this.availableInstances = new ArrayBlockingQueue<>(poolSize);
        log.info("Browser pool configured with {} instance(s), borrow timeout {}s", poolSize, borrowTimeoutSec);
    }

    /**
     * Takes an instance for exclusive use, launching a new browser while the pool has not reached its
     * size yet and otherwise waiting for another thread to return one. The caller must pass the
     * instance to {@link #returnInstance(BrowserInstance)} once its page is finished.
     *
     * @throws IOException when no instance became available within the borrow timeout, so a stuck
     *                     page fails instead of blocking the whole parsing run
     */
    public BrowserInstance borrowInstance() throws IOException {
        BrowserInstance pooledInstance = availableInstances.poll();
        if (pooledInstance != null) {
            return pooledInstance;
        }

        if (launchedInstancesCnt.incrementAndGet() <= poolSize) {
            log.info("Launching browser instance {}/{}", launchedInstancesCnt.get(), poolSize);
            try {
                return new BrowserInstance();
            } catch (RuntimeException e) {
                launchedInstancesCnt.decrementAndGet();
                throw new IOException("Could not launch a browser instance", e);
            }
        }
        launchedInstancesCnt.decrementAndGet();

        try {
            BrowserInstance waitedInstance = availableInstances.poll(borrowTimeoutSec, TimeUnit.SECONDS);
            if (waitedInstance == null) {
                throw new IOException(String.format(
                        "No browser instance became available within %d seconds", borrowTimeoutSec));
            }
            return waitedInstance;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for a browser instance", e);
        }
    }

    /**
     * Returns an instance to the pool, discarding it when its Chromium died so the next borrower
     * launches a healthy one instead of inheriting a broken browser.
     */
    public void returnInstance(BrowserInstance instance) {
        if (!instance.isConnected()) {
            log.warn("Discarding disconnected browser instance");
            discardInstance(instance);
            return;
        }

        if (!availableInstances.offer(instance)) {
            log.warn("Browser pool is already full, closing the returned instance");
            discardInstance(instance);
        }
    }

    private void discardInstance(BrowserInstance instance) {
        launchedInstancesCnt.decrementAndGet();
        instance.close();
    }

    @PreDestroy
    public void closeInstances() {
        List<BrowserInstance> drainedInstances = new ArrayList<>();
        availableInstances.drainTo(drainedInstances);
        log.info("Closing {} pooled browser instance(s)", drainedInstances.size());
        drainedInstances.forEach(BrowserInstance::close);
    }
}
