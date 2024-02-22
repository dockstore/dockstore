package io.dockstore.webservice.helpers;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DebugHelper {

    private static final Logger LOG = LoggerFactory.getLogger(DebugHelper.class);

    private static DockstoreWebserviceConfiguration config;

    private static MemoryMXBean memoryMXBean;
    private static ThreadMXBean threadMXBean;

    private DebugHelper() {
    }

    public static void setConfig(DockstoreWebserviceConfiguration config) {
        DebugHelper.config = config;
        init();
    }

    private static void init() {
        // initialize the beans
        memoryMXBean = ManagementFactory.getMemoryMXBean();
        threadMXBean = ManagementFactory.getThreadMXBean();
    }

    public static void dumpGlobals() {
        log("threads", () -> formatThreads());
        log("processes", () -> formatProcesses());
        log("database", () -> formatDatabase());
        log("memory", () -> formatMemory());
    }

    public static String formatThreads() {
        return concat(threadMXBean.dumpAllThreads(true, true));
    }

    public static String formatProcesses() {
        return "processes";
    }

    public static String formatDatabase() {
        return "database";
    }

    public static String formatMemory() {
        return nameValue("HEAP", memoryMXBean.getHeapMemoryUsage()) + nameValue("NON-HEAP", memoryMXBean.getNonHeapMemoryUsage());
    }

    private static void log(String name, Supplier<String> valueSupplier) {
        if (LOG.isInfoEnabled()) {
            LOG.info(String.format("%s:\n%s", name, valueSupplier.get()));
        }
    }

    private static String nameValue(String name, Object value) {
        return String.format("%s: %s\n", name, value);
    }

    private static String concat(Object... objects) {
        return Arrays.asList(objects).stream().map(Object::toString).collect(Collectors.joining());
    }

    public static ApplicationEventListener getApplicationEventListener() {
        return new DebugHelperApplicationEventListener();
    }

    // https://eclipse-ee4j.github.io/jersey.github.io/documentation/latest3x/monitoring_tracing.html
    public static class DebugHelperApplicationEventListener implements ApplicationEventListener {
        @Override
        public void onEvent(ApplicationEvent event) {
            LOG.error("APP onEvent " + event.getType());
        }

        @Override
        public RequestEventListener onRequest(RequestEvent event) {
            LOG.error("APP onRequest " + event.getType() + " " + event.getContainerRequest() + " " + event.getContainerResponse());
            return new DebugHelperRequestEventListener();
        }
    }

    public static class DebugHelperRequestEventListener implements RequestEventListener {
        @Override
        public void onEvent(RequestEvent event) {
            LOG.error("REQUEST onRequest " + event.getType() + " " + event.getContainerRequest() + " " + event.getContainerResponse());
            if (event.getType() == RequestEvent.Type.FINISHED) {
                dumpGlobals();
            }
        }
    }
}
