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

    private static DockstoreWebserviceConfiguration config;
    private static Environment environment;
    private static SessionFactory sessionFactory;

    private static MemoryMXBean memoryMXBean;
    private static ThreadMXBean threadMXBean;

    private static CensorHelper censorHelper;

    static {
        // Instantiate the censoring support.
        censorHelper = new CensorHelper(readFrequencies());
    }

    private DiagnosticsHelper() {
        // This space intentionally left blank.
    }

    public static void init(DockstoreWebserviceConfiguration newConfig, Environment newEnvironment, SessionFactory newSessionFactory) {

        // If diagnostic output is not enabled, return without doing anything.
        if (!newConfig.getDiagnosticsConfig().getEnabled()) {
            return;
        }

        DiagnosticsHelper.config = newConfig;
        DiagnosticsHelper.environment = newEnvironment;
        DiagnosticsHelper.sessionFactory = newSessionFactory;

        // Initialize the beans that we know are singletons, just in case there are performance issues with repeatedly retrieving them.
        memoryMXBean = ManagementFactory.getMemoryMXBean();
        threadMXBean = ManagementFactory.getThreadMXBean();

        // Create a daemon-thread-backed Timer and schedule a periodic dump of the global information.
        long periodMilliseconds = Math.round(newConfig.getDiagnosticsConfig().getPeriodSeconds() * MILLISECONDS_PER_SECOND);
        new Timer("diagnostics", true).scheduleAtFixedRate(
            new TimerTask() {
                public void run() {
                    logGlobals();
                }
            }, periodMilliseconds, periodMilliseconds);

        // Register a Jersey event handler that will log session and thread-related information.
        environment.jersey().register(new DiagnosticsHelperApplicationEventListener());
    }

    public static void logGlobals() {
        logThreads();
        logProcesses();
        logFilesystems();
        logDatabase();
        logMemory();
    }

    public static void logThreads() {
        log("threads", () -> formatThreads());
    }

    public static void logProcesses() {
        log("processes", () -> formatProcesses());
    }

    public static void logFilesystems() {
        log("filesystems", () -> formatFilesystems());
    }

    public static void logDatabase() {
        log("database", () -> formatDatabase());
    }

    public static void logMemory() {
        log("memory", () -> formatMemory());
    }

    public static void logStarted(ContainerRequest request) {
        log("started", () -> formatRequest(request));
    }

    public static void logFinished(ContainerRequest request, ContainerResponse response) {
        log("finished", () -> formatResponse(request, response));
    }

    public static void logThread(ThreadState startState, ThreadState finishState) {
        log("thread", () -> formatThread(startState, finishState));
    }

    public static void logSession(Session session) {
        log("session", () -> formatSession(session));
    }

    public static void log(String name, Supplier<String> valueSupplier) {
        if (LOG.isInfoEnabled()) {
            Thread current = Thread.currentThread();
            String message = String.format("debug.%s by thread \"%s\" (%s):\n%s", name, current.getName(), current.getId(), valueSupplier.get());
            LOG.info(censor(message));
        }
    }

    public static String censor(String s) {
        return censorHelper.censor(s);
    }

    public static String formatThreads() {
        return concat(Arrays.asList(threadMXBean.dumpAllThreads(true, true)));
    }

    public static String formatProcesses() {
        return outputFromCommand("ps -A -O ppid,ruser,pri,pcpu,pmem,rss");
    }

    public static String formatFilesystems() {
        return outputFromCommand("df");
    }

    // https://metrics.dropwizard.io/4.2.0/
    public static String formatDatabase() {
        Map<String, Gauge> gauges = environment.metrics().getGauges();
        return nameValue("pool-size", gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.size").getValue())
            + nameValue("pool-active", gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.active").getValue())
            + nameValue("pool-idle", gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.idle").getValue());
    }

    public static String formatMemory() {
        return nameValue("HEAP", memoryMXBean.getHeapMemoryUsage())
            + nameValue("NON-HEAP", memoryMXBean.getNonHeapMemoryUsage())
            + concat(format(DiagnosticsHelper::formatMemoryPoolMXBean, ManagementFactory.getMemoryPoolMXBeans()));
    }

    public static String formatSession(Session session) {
        return session.getStatistics().toString();
    }

    public static String formatThread(ThreadState startState, ThreadState finishState) {
        return nameValue("allocated", formatBytes(finishState.allocatedBytes() - startState.allocatedBytes()))
            + nameValue("cpu-time", formatNanoseconds(finishState.cpuTime() - startState.cpuTime()))
            + nameValue("user-time", formatNanoseconds(finishState.userTime() - startState.userTime()))
            + nameValue("elapsed-time", formatNanoseconds(finishState.wallClock() - startState.wallClock()));
    }

    public static String formatNanoseconds(long ns) {
        return String.format("%.3f sec", ns / NANOSECONDS_PER_SECOND);
    }

    public static String formatBytes(long bytes) {
        return String.format("%.2f MB", bytes / BYTES_PER_MEGABYTE);
    }

    private static String formatMemoryPoolMXBean(MemoryPoolMXBean pool) {
        return nameValue("POOL", pool.getName() + ", " + pool.getType())
            + nameValue("current", pool.getUsage())
            + nameValue("peak", pool.getPeakUsage())
            + nameValue("collection", pool.getCollectionUsage());
    }

    private static String formatRequest(ContainerRequest request) {
        return String.format("%s \"%s\"", request.getMethod(), request.getPath(false));
    }

    private static String formatResponse(ContainerRequest request, ContainerResponse response) {
        return formatRequest(request);
    }

    private static String nameValue(String name, Object value) {
        return String.format("%s: %s\n", name, value);
    }

    private static <T> List<String> format(Function<T, String> formatter, Collection<T> objects) {
        return objects.stream().map(formatter).toList();
    }

    private static String concat(Collection<?> objects) {
        return objects.stream().map(Object::toString).collect(Collectors.joining());
    }

    private static ThreadState getState() {
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

    private static Optional<Session> getCurrentSession() {
        try {
            // This getCurrentSession() call will throw if there's no current session.
            return Optional.of(sessionFactory.getCurrentSession());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String outputFromCommand(String command) {
        return Utilities.executeCommand(command).getLeft();
    }

    private static void handleRequestEvent(RequestEvent event, ThreadState startState) {
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

    private static Map<String, Double> readFrequencies() {
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
    public static class DiagnosticsHelperApplicationEventListener implements ApplicationEventListener {
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

    public static class DiagnosticsHelperRequestEventListener implements RequestEventListener {
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
