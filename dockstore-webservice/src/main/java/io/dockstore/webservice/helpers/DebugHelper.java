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

        // Register a Jersey event handler that will field request events and log session and thread-related information.
        environment.jersey().register(new DebugHelperApplicationEventListener());
    }

    public static void logGlobals() {
        log("threads", () -> formatThreads());
        log("processes", () -> formatProcesses());
        log("database", () -> formatDatabase());
        log("memory", () -> formatMemory());
    }

    private static void log(String name, Supplier<String> valueSupplier) {
        if (LOG.isInfoEnabled()) {
            Thread current = Thread.currentThread();
            LOG.info(String.format("debug.%s by thread \"%s\" (%s):\n%s", name, current.getName(), current.getId(), valueSupplier.get()));
        }
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
                log("started", () -> formatRequest(request));
                break;

            // Done filtering response.
            case RESP_FILTERS_FINISHED:
                // This is the last stage at which the Hibernate session is bound.
                // If the session was closed/dissociated during the request, it may not even be available here.
                getCurrentSession().ifPresent(session -> log("session", () -> formatSession(session)));
                break;

            // Request finished.
            case FINISHED:
                log("finished", () -> formatRequest(request));
                log("thread", () -> formatThread(startState, getState()));
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
