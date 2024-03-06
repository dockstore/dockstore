package io.dockstore.webservice.helpers;

import com.codahale.metrics.Gauge;
import com.google.common.io.Resources;
import io.dockstore.common.Utilities;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.core.setup.Environment;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DiagnosticsHelper {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticsHelper.class);
    private static final double MILLISECONDS_PER_SECOND = 1e3;
    private static final double NANOSECONDS_PER_SECOND = 1e9;
    private static final double BYTES_PER_MEGABYTE = 1e6;

    private DockstoreWebserviceConfiguration config;
    private Environment environment;
    private SessionFactory sessionFactory;

    private Logger logger;
    private CensorHelper censorHelper;
    private MemoryMXBean memoryMXBean;
    private ThreadMXBean threadMXBean;

    public DiagnosticsHelper() {
        this(LOG);
    }

    public DiagnosticsHelper(Logger logger) {
        this.logger = logger;
        this.censorHelper = new CensorHelper(readFrequencies());
        // Initialize the beans that we know are singletons, just in case there are performance issues with repeatedly retrieving them.
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
    }

    public void start(Environment environment, SessionFactory sessionFactory, double periodSeconds) {

        // Create a daemon-thread-backed Timer and schedule a periodic dump of the global information.
        long periodMilliseconds = Math.round(periodSeconds * MILLISECONDS_PER_SECOND);
        new Timer("diagnostics", true).scheduleAtFixedRate(
            new TimerTask() {
                public void run() {
                    logGlobals();
                }
            }, periodMilliseconds, periodMilliseconds);

        // Register a Jersey event handler that will log session and thread-related information.
        environment.jersey().register(new DiagnosticsHelperApplicationEventListener());
    }

    public void logGlobals() {
        logThreads();
        logProcesses();
        logFilesystems();
        logDatabase();
        logMemory();
    }

    public void logThreads() {
        log("threads", () -> formatThreads());
    }

    public void logProcesses() {
        log("processes", () -> formatProcesses());
    }

    public void logFilesystems() {
        log("filesystems", () -> formatFilesystems());
    }

    public void logDatabase() {
        log("database", () -> formatDatabase());
    }

    public void logMemory() {
        log("memory", () -> formatMemory());
    }

    public void logStarted(ContainerRequest request) {
        log("started", () -> formatRequest(request));
    }

    public void logFinished(ContainerRequest request, ContainerResponse response) {
        log("finished", () -> formatResponse(request, response));
    }

    public void logThread(ThreadState startState, ThreadState finishState) {
        log("thread", () -> formatThread(startState, finishState));
    }

    public void logSession(Session session) {
        log("session", () -> formatSession(session));
    }

    public void log(String type, Supplier<String> valueSupplier) {
        if (logger.isInfoEnabled()) {
            Thread current = Thread.currentThread();
            String message = String.format("debug.%s by thread \"%s\" (%s):\n%s", type, current.getName(), current.getId(), valueSupplier.get());
            logger.info(censorHelper.censor(message));
        }
    }

    public String formatThreads() {
        return concat(Arrays.asList(threadMXBean.dumpAllThreads(true, true)));
    }

    public String formatProcesses() {
        return outputFromCommand("ps -A -O ppid,ruser,pri,pcpu,pmem,rss");
    }

    public String formatFilesystems() {
        return outputFromCommand("df");
    }

    // https://metrics.dropwizard.io/4.2.0/
    public String formatDatabase() {
        Map<String, Gauge> gauges = environment.metrics().getGauges();
        return nameValue("pool-size", gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.size").getValue())
            + nameValue("pool-active", gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.active").getValue())
            + nameValue("pool-idle", gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.idle").getValue());
    }

    public String formatMemory() {
        return nameValue("HEAP", memoryMXBean.getHeapMemoryUsage())
            + nameValue("NON-HEAP", memoryMXBean.getNonHeapMemoryUsage())
            + concat(format(this::formatMemoryPoolMXBean, ManagementFactory.getMemoryPoolMXBeans()));
    }

    public String formatSession(Session session) {
        return session.getStatistics().toString();
    }

    public String formatThread(ThreadState startState, ThreadState finishState) {
        return nameValue("allocated", formatBytes(finishState.allocatedBytes() - startState.allocatedBytes()))
            + nameValue("cpu-time", formatNanoseconds(finishState.cpuTime() - startState.cpuTime()))
            + nameValue("user-time", formatNanoseconds(finishState.userTime() - startState.userTime()))
            + nameValue("elapsed-time", formatNanoseconds(finishState.wallClock() - startState.wallClock()));
    }

    public String formatNanoseconds(long ns) {
        return String.format("%.3f sec", ns / NANOSECONDS_PER_SECOND);
    }

    public String formatBytes(long bytes) {
        return String.format("%.2f MB", bytes / BYTES_PER_MEGABYTE);
    }

    private String formatMemoryPoolMXBean(MemoryPoolMXBean pool) {
        return nameValue("POOL", pool.getName() + ", " + pool.getType())
            + nameValue("current", pool.getUsage())
            + nameValue("peak", pool.getPeakUsage())
            + nameValue("collection", pool.getCollectionUsage());
    }

    private String formatRequest(ContainerRequest request) {
        return String.format("%s \"%s\"", request.getMethod(), request.getPath(false));
    }

    private String formatResponse(ContainerRequest request, ContainerResponse response) {
        return formatRequest(request);
    }

    private String nameValue(String name, Object value) {
        return String.format("%s: %s\n", name, value);
    }

    private <T> List<String> format(Function<T, String> formatter, Collection<T> objects) {
        return objects.stream().map(formatter).toList();
    }

    private String concat(Collection<?> objects) {
        return objects.stream().map(Object::toString).collect(Collectors.joining());
    }

    private ThreadState getState() {
        long bytes = 0;
        if (threadMXBean instanceof com.sun.management.ThreadMXBean sunBean) {
            bytes = sunBean.getCurrentThreadAllocatedBytes();
        } else {
            LOG.info("sun threadMXBean.getCurrentThreadAllocatedBytes() not supported");
        }
        long cpuTime = 0;
        try {
            cpuTime = threadMXBean.getCurrentThreadCpuTime();
        } catch (UnsupportedOperationException e) {
            LOG.info("threadMXBean.getCurrentThreadCpuTime not supported");
        }
        long userTime = 0;
        try {
            userTime = threadMXBean.getCurrentThreadUserTime();
        } catch (UnsupportedOperationException e) {
            LOG.info("threadMXBean.getCurrentThreadUserTime not supported");
        }
        long wallClock = System.nanoTime();
        return new ThreadState(bytes, cpuTime, userTime, wallClock);
    }

    private Optional<Session> getCurrentSession() {
        try {
            // This getCurrentSession() call will throw if there's no current session.
            return Optional.of(sessionFactory.getCurrentSession());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String outputFromCommand(String command) {
        return Utilities.executeCommand(command).getLeft();
    }

    private void handleRequestEvent(RequestEvent event, ThreadState startState) {
        try {
            ContainerRequest request = event.getContainerRequest();
            ContainerResponse response = event.getContainerResponse();

            switch (event.getType()) {

            // Request started.
            case START:
                logStarted(request);
                break;

            // Done filtering response.
            case RESP_FILTERS_FINISHED:
                // Experimentally, we've determined that soon after this, the Hibernate session is dissociated from the thread.
                // If the session was closed during the request, it may not even be available here.
                getCurrentSession().ifPresent(session -> logSession(session));
                break;

            // Request finished.
            case FINISHED:
                logFinished(request, response);
                logThread(startState, getState());
                break;

            // Do nothing for other events.
            default:
                break;
            }

        } catch (Exception e) {
            // An Exception thrown by this handler will cause the request to fail, so we catch and suppress it.
            LOG.error("Request handler threw", e);
        }
    }

    private Map<String, Double> readFrequencies() {
        Map<String, Double> tripletToFrequency = new HashMap<>();
        String content;
        try {
            content = Resources.toString(Resources.getResource("english_triplet_frequencies.txt"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Error reading frequency file", e);
        }
        for (String line: content.split("\n")) {
            String[] fields = line.split(",");
            tripletToFrequency.put(fields[0], Double.parseDouble(fields[1]));
        }
        for (String terms: List.of("cwl", "wdl", "nfl")) {
            tripletToFrequency.put(terms, tripletToFrequency.get("the"));
        }
        return tripletToFrequency;
    }

    // https://eclipse-ee4j.github.io/jersey.github.io/documentation/latest3x/monitoring_tracing.html
    public class DiagnosticsHelperApplicationEventListener implements ApplicationEventListener {
        @Override
        public void onEvent(ApplicationEvent event) {
            // This space intentionally left blank.
        }

        @Override
        public RequestEventListener onRequest(RequestEvent event) {
            ThreadState startState = getState();
            handleRequestEvent(event, startState);
            return new DiagnosticsHelperRequestEventListener(startState);
        }
    }

    public class DiagnosticsHelperRequestEventListener implements RequestEventListener {
        private final ThreadState startState;

        public DiagnosticsHelperRequestEventListener(ThreadState startState) {
            this.startState = startState;
        }

        @Override
        public void onEvent(RequestEvent event) {
            handleRequestEvent(event, startState);
        }
    }

    private static record ThreadState (long allocatedBytes, long cpuTime, long userTime, long wallClock) {
    }
}
