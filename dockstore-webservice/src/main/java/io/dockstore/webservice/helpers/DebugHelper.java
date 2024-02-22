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

    public static void setConfig(DockstoreWebserviceConfiguration config) {
        DebugHelper.config = config;
        init();
    }

    public static void setEnvironment(Environment environment) {
        DebugHelper.environment = environment;
    }

    public static void setSessionFactory(SessionFactory sessionFactory) {
        DebugHelper.sessionFactory = sessionFactory;
    }

    private static void init() {
        // initialize the beans
        memoryMXBean = ManagementFactory.getMemoryMXBean();
        threadMXBean = ManagementFactory.getThreadMXBean();
        new Timer("diagnostics", true).scheduleAtFixedRate(
           new TimerTask() {
               public void run() {
                   dumpGlobals();
               }
           }, 0, 10000);
    }

    public static void dumpGlobals() {
        log("threads", () -> formatThreads());
        log("processes", () -> formatProcesses());
        log("database", () -> formatDatabase());
        log("memory", () -> formatMemory());
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
            concat(format(DebugHelper::memoryPoolMXBeanToString, ManagementFactory.getMemoryPoolMXBeans()));
    }

    public static String formatSession() {
        Session session = sessionFactory.getCurrentSession();
        SessionStatistics statistics = session.getStatistics();
        return statistics.toString();
    }

    public static String formatNanoseconds(long ns) {
        return String.format("%.3f sec", ns / 1e9);
    }

    public static String formatBytes(long bytes) {
        return String.format("%.2f MB", bytes / 1e6);
    }

    private static String memoryPoolMXBeanToString(MemoryPoolMXBean pool) {
        return nameValue("POOL", pool.getName() + ", " + pool.getType()) +
            nameValue("current", pool.getUsage()) +
            nameValue("peak", pool.getPeakUsage()) +
            nameValue("collection", pool.getCollectionUsage());
    }

    private static void log(String name, Supplier<String> valueSupplier) {
        if (LOG.isInfoEnabled()) {
            Thread current = Thread.currentThread();
            LOG.info(String.format("debug.%s by thread \"%s\" (%s):\n%s", name, current.getName(), current.getId(), valueSupplier.get()));
        }
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
        }
        try {
            cpuTime = threadMXBean.getCurrentThreadCpuTime();
        }
        catch (UnsupportedOperationException e) {
            LOG.error("threadMXBean.getCurrentThreadCpuTime not supported");
        }
        try {
            userTime = threadMXBean.getCurrentThreadUserTime();
        }
        catch (UnsupportedOperationException e) {
            LOG.error("threadMXBean.getCurrentThreadUserTime not supported");
        }
        long wallClock = System.nanoTime();
        return new ThreadState(bytes, cpuTime, userTime, wallClock);
    }

    private static boolean hasSession() {
        try {
            sessionFactory.getCurrentSession();
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    public static ApplicationEventListener getApplicationEventListener() {
        return new DebugHelperApplicationEventListener();
    }

    private static String requestToString(ContainerRequest request) {
        return String.format("%s \"%s\"", request.getMethod(), request.getPath(false));
    }

    private static String requestAndThreadToString(ContainerRequest request) {
        Thread current = Thread.currentThread();
        return String.format("%s in thread \"%s\" (%s)", requestToString(request), current.getName(), current.getId());
    }

    private static void handleRequestEvent(RequestEvent event, ThreadState startState) {
        // log("event", () -> event.getType() + " " + sessionFactory.getCurrentSession());
        ContainerRequest request = event.getContainerRequest();
        ContainerResponse response = event.getContainerResponse();
        switch (event.getType()) {
            case START:
                log("started", () -> requestToString(request));
                break;
            case RESP_FILTERS_FINISHED:
                if (hasSession()) {
                    log("session", () -> formatSession());
                }
                break;
            case FINISHED:
                log("finished", () -> requestToString(request));
                log("thread", () ->
                    nameValue("allocated", formatBytes(getState().allocatedBytes() - startState.allocatedBytes())) +
                    nameValue("cpu-time", formatNanoseconds(getState().cpuTime() - startState.cpuTime())) +
                    nameValue("user-time", formatNanoseconds(getState().userTime() - startState.userTime())) +
                    nameValue("elapsed-time", formatNanoseconds(getState().wallClock() - startState.wallClock()))
                );
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
