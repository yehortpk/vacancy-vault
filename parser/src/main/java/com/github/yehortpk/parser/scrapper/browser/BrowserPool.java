package com.github.yehortpk.parser.scrapper.browser;

import com.github.yehortpk.parser.exceptions.ParsingStoppedException;
import com.github.yehortpk.parser.services.ParsingProgressService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.management.ManagementFactory;
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
 * <p>
 * The size is either set explicitly or, with {@code max}, the most the host can afford: half its
 * CPUs, as each rendering Chromium keeps about one core busy, but never more than the memory left
 * once the rest of the stack is accounted for. Both are read container-aware, so a compose limit
 * counts.
 * <p>
 * Borrowers queue without a deadline: every page of every dynamic company is queued at once, so a
 * deadline would fail healthy pages merely for waiting their turn. A page stuck in Chromium is
 * bounded by Playwright's own timeouts instead, and a stop request releases the queue.
 */
@Slf4j
@Component
public class BrowserPool {
    private static final int STOP_CHECK_INTERVAL_SEC = 1;
    private static final String MAX_POOL_SIZE = "max";
    private static final long MB = 1024L * 1024;
    private static final long CHROMIUM_INSTANCE_MEMORY_BYTES = 400 * MB;
    /**
     * Memory the browsers must leave alone on a host shared with the compose stack: about 2GB for
     * Kafka, MySQL, the router and the rest, plus about 1GB for the parser's Maven and app JVMs.
     */
    private static final long RESERVED_MEMORY_BYTES = 3 * 1024 * MB;

    private final BlockingQueue<BrowserInstance> availableInstances;
    private final AtomicInteger launchedInstancesCnt = new AtomicInteger();
    private final int poolSize;
    private final ParsingProgressService parsingProgressService;

    public BrowserPool(
            @Value("${parser.browser.pool-size}") String configuredPoolSize,
            ParsingProgressService parsingProgressService
    ) {
        this.poolSize = MAX_POOL_SIZE.equals(configuredPoolSize)
                ? calculateHostPoolSize()
                : parsePoolSize(configuredPoolSize);
        this.parsingProgressService = parsingProgressService;
        this.availableInstances = new ArrayBlockingQueue<>(poolSize);
        log.info("Browser pool configured with {} instance(s)", poolSize);
    }

    private static int parsePoolSize(String configuredPoolSize) {
        try {
            int poolSize = Integer.parseInt(configuredPoolSize.trim());
            if (poolSize >= 1) {
                return poolSize;
            }
        } catch (NumberFormatException ignored) {
            // Reported below together with the out-of-range case
        }
        throw new IllegalArgumentException(String.format(
                "Browser pool size must be '%s' or a positive number, got '%s'", MAX_POOL_SIZE, configuredPoolSize));
    }

    private static int calculateHostPoolSize() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        long totalMemoryBytes = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
                .getTotalMemorySize();
        int poolSize = calculatePoolSize(availableProcessors, totalMemoryBytes);
        log.info("Browser pool sized from the host: {} CPU(s), {}MB of memory", availableProcessors, totalMemoryBytes / MB);
        return poolSize;
    }

    /**
     * Half the CPUs, capped by how many Chromium instances fit into the memory left after the
     * reserve. Never below one, so a small host still renders dynamic sites, only slower.
     */
    static int calculatePoolSize(int availableProcessors, long totalMemoryBytes) {
        long cpuBoundSize = availableProcessors / 2;
        long memoryBoundSize = (totalMemoryBytes - RESERVED_MEMORY_BYTES) / CHROMIUM_INSTANCE_MEMORY_BYTES;
        return (int) Math.max(1, Math.min(cpuBoundSize, memoryBoundSize));
    }

    /**
     * Takes an instance for exclusive use, launching a new browser while the pool has not reached its
     * size yet and otherwise waiting for another thread to return one. The caller must pass the
     * instance to {@link #returnInstance(BrowserInstance)} once its page is finished.
     *
     * @throws ParsingStoppedException when parsing is stopped while waiting, so queued pages are
     *                                 skipped instead of each still taking a browser in turn
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
            BrowserInstance waitedInstance;
            while ((waitedInstance = availableInstances.poll(STOP_CHECK_INTERVAL_SEC, TimeUnit.SECONDS)) == null) {
                if (parsingProgressService.isStopRequested()) {
                    throw new ParsingStoppedException();
                }
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
