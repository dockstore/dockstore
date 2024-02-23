package io.dockstore.webservice.helpers;

import com.codahale.metrics.Gauge;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.common.Utilities;
import io.dropwizard.core.setup.Environment;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.hibernate.SessionFactory;
import org.hibernate.Session;
import org.hibernate.stat.SessionStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DebugHelper {

    private static final Logger LOG = LoggerFactory.getLogger(DebugHelper.class);

    private static DockstoreWebserviceConfiguration config;
    private static Environment environment;
    private static SessionFactory sessionFactory;

    private static MemoryMXBean memoryMXBean;
    private static ThreadMXBean threadMXBean;

    private DebugHelper() {
    }

    public static void init(DockstoreWebserviceConfiguration config, Environment environment, SessionFactory sessionFactory) {

        DebugHelper.config = config;
        DebugHelper.environment = environment;
        DebugHelper.sessionFactory = sessionFactory;

        // Initialize the beans that we know are singletons, just in case there are performance issues with repeatedly retrieving them.
        memoryMXBean = ManagementFactory.getMemoryMXBean();
        threadMXBean = ManagementFactory.getThreadMXBean();

        // Create a Timer, backed by a daemon thread, and schedule a periodic dump of the global information.
        new Timer("diagnostics", true).scheduleAtFixedRate(
            new TimerTask() {
                public void run() {
                    logGlobals();
                }
            }, 10000, 10000);

        // Register a Jersey event handler that will log session and thread-related information.
        environment.jersey().register(new DebugHelperApplicationEventListener());
    }

    public static void logGlobals() {
        logThreads();
        logProcesses();
        logDatabase();
        logMemory();
    }

    public static void logThreads() {
        log("threads", () -> formatThreads());
    }

    public static void logProcesses() {
        log("processes", () -> formatProcesses());
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

    public static void logFinished(ContainerRequest request) {
        log("finished", () -> formatRequest(request));
    }

    public static void logThread(ThreadState startState, ThreadState finishState) {
        log("thread", () -> formatThread(startState, finishState));
    }

    public static void logSession(Session session) {
        log("session", () -> formatSession(session));
    }

    private static void log(String name, Supplier<String> valueSupplier) {
        if (LOG.isInfoEnabled()) {
            Thread current = Thread.currentThread();
            String message = String.format("debug.%s by thread \"%s\" (%s):\n%s", name, current.getName(), current.getId(), valueSupplier.get());
            LOG.info(censor(message));
        }
    }

    private static String censor(String value) {
        // Censor any continuous run of 40 or more base64-legal non-padding
        // characters if it's either: a) a hexadecimal string, or b) "scrambled".
        // In this context, "scrambled" means that there's a typical mix of the
        // different character classes, indicative of a well-encoded key.
        // This code will censor all but approximately 1 of 13000 random
        // 40-character strings made up of base64-legal non-padding characters
        // with uniform character frequency, but passes most of information we're
        // logging with no modifications.
        StringBuilder censored = new StringBuilder(value);
        Pattern suspectPattern = Pattern.compile("[-_a-zA-Z0-9+/]{40,}");
        Pattern hexPattern = Pattern.compile("^[a-fA-F0-9]*$");
        Matcher matcher = suspectPattern.matcher(value);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String suspect = decapitalize(value.substring(start, end));
            if (hexPattern.matcher(suspect).matches() || isScrambled(suspect)) {
                for (int i = start; i < end; i++) {
                   censored.setCharAt(i, 'X');
                }
            }
        }
        return censored.toString();
    }

    private static String decapitalize(String v) {
        StringBuilder builder = new StringBuilder(v);
        for (int i = 1, n = v.length() - 1; i < n; i++) {
            char before = v.charAt(i - 1);
            char c = v.charAt(i);
            char after = v.charAt(i + 1);
            if (before == '/' && c >= 'A' && c <= 'Z' && after >= 'a' && after <= 'z') {
                builder.setCharAt(i, (char)('a' + (c - 'A')));
            }
        }
        return builder.toString();
    }

    private static boolean isScrambled(String v) {
        int adjacents = 0;
        for (int i = 0, n = v.length() - 1; i < n; i++) {
            char a = v.charAt(i);
            char b = v.charAt(i + 1);
            if (characterClass(a) == characterClass(b)) {
                adjacents++;
            }
        }
        return adjacents < (3 * v.length()) / 4;
    }

    private static int characterClass(char c) {
        if (c >= '0' && c <= '9') return 0;
        if (c >= 'A' && c <= 'Z') return 1;
        return 2;
    }

    public static String formatThreads() {
        return concat(Arrays.asList(threadMXBean.dumpAllThreads(true, true)));
    }

    public static String formatProcesses() {
        return Utilities.executeCommand("ps -A -O ppid,ruser,pri,pcpu,pmem,rss").getLeft();
    }

    // https://metrics.dropwizard.io/4.2.0/
    public static String formatDatabase() {
        Map<String, Gauge> gauges = environment.metrics().getGauges();
        return nameValue("pool-size", gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.size").getValue()) +
            nameValue("pool-active", gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.active").getValue()) +
            nameValue("pool-idle", gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.idle").getValue());
    }

    public static String formatMemory() {
        return nameValue("HEAP", memoryMXBean.getHeapMemoryUsage()) +
            nameValue("NON-HEAP", memoryMXBean.getNonHeapMemoryUsage()) +
            concat(format(DebugHelper::formatMemoryPoolMXBean, ManagementFactory.getMemoryPoolMXBeans()));
    }

    public static String formatSession(Session session) {
        return session.getStatistics().toString();
    }

    public static String formatThread(ThreadState startState, ThreadState finishState) {
        return nameValue("allocated", formatBytes(finishState.allocatedBytes() - startState.allocatedBytes())) +
            nameValue("cpu-time", formatNanoseconds(finishState.cpuTime() - startState.cpuTime())) +
            nameValue("user-time", formatNanoseconds(finishState.userTime() - startState.userTime())) +
            nameValue("elapsed-time", formatNanoseconds(finishState.wallClock() - startState.wallClock()));
    }

    public static String formatNanoseconds(long ns) {
        return String.format("%.3f sec", ns / 1e9);
    }

    public static String formatBytes(long bytes) {
        return String.format("%.2f MB", bytes / 1e6);
    }

    private static String formatMemoryPoolMXBean(MemoryPoolMXBean pool) {
        return nameValue("POOL", pool.getName() + ", " + pool.getType()) +
            nameValue("current", pool.getUsage()) +
            nameValue("peak", pool.getPeakUsage()) +
            nameValue("collection", pool.getCollectionUsage());
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
        long bytes = 0, cpuTime = 0, userTime = 0;
        if (threadMXBean instanceof com.sun.management.ThreadMXBean sunBean) {
            bytes = sunBean.getCurrentThreadAllocatedBytes();
        } else {
            LOG.info("sun threadMXBean.getCurrentThreadAllocatedBytes() not supported");
        }
        try {
            cpuTime = threadMXBean.getCurrentThreadCpuTime();
        }
        catch (UnsupportedOperationException e) {
            LOG.info("threadMXBean.getCurrentThreadCpuTime not supported");
        }
        try {
            userTime = threadMXBean.getCurrentThreadUserTime();
        }
        catch (UnsupportedOperationException e) {
            LOG.info("threadMXBean.getCurrentThreadUserTime not supported");
        }
        long wallClock = System.nanoTime();
        return new ThreadState(bytes, cpuTime, userTime, wallClock);
    }

    private static Optional<Session> getCurrentSession() {
        try {
            // This getCurrentSession() call will throw if there's no current session.
            return Optional.of(sessionFactory.getCurrentSession());
        }
        catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String formatRequest(ContainerRequest request) {
        return String.format("%s \"%s\"", request.getMethod(), request.getPath(false));
    }

    private static void handleRequestEvent(RequestEvent event, ThreadState startState) {

        ContainerRequest request = event.getContainerRequest();
        ContainerResponse response = event.getContainerResponse();

        switch (event.getType()) {

            // Request started.
            case START:
                logStarted(request);
                break;

            // Done filtering response.
            case RESP_FILTERS_FINISHED:
                // This is the last stage at which the Hibernate session is bound.
                // If the session was closed/dissociated during the request, it may not even be available here.
                getCurrentSession().ifPresent(session -> logSession(session));
                break;

            // Request finished.
            case FINISHED:
                logFinished(request);
                logThread(startState, getState());
                break;
        }
    }

    // https://eclipse-ee4j.github.io/jersey.github.io/documentation/latest3x/monitoring_tracing.html
    public static class DebugHelperApplicationEventListener implements ApplicationEventListener {
        @Override
        public void onEvent(ApplicationEvent event) {
            // This space intentionally left blank.
        }

        @Override
        public RequestEventListener onRequest(RequestEvent event) {
            ThreadState startState = getState();
            handleRequestEvent(event, startState);
            return new DebugHelperRequestEventListener(startState);
        }
    }

    public static class DebugHelperRequestEventListener implements RequestEventListener {
        private final ThreadState startState;
        public DebugHelperRequestEventListener(ThreadState startState) {
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
