package io.dockstore.webservice.helpers;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.glassfish.jersey.spi.ExtendedExceptionMapper;
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
        return join(threadMXBean.dumpAllThreads(true, true));
    }

    public static String formatProcesses() {
        return "processes";
    }

    public static String formatDatabase() {
        return "database";
    }

    public static String formatMemory() {
        return join(nameValue("heap", memoryMXBean.getHeapMemoryUsage()), nameValue("non-heap", memoryMXBean.getNonHeapMemoryUsage()));
    }

    private static void log(String name, Supplier<String> valueSupplier) {
        if (LOG.isInfoEnabled()) {
            LOG.info(nameValue("debug:" + name, valueSupplier.get()));
        }
    }

    private static String nameValue(String name, Object value) {
        return String.format("%s = %s", name, value);
    }

    private static String join(Object... objects) {
        return join(Arrays.asList(objects));
    }

    private static String join(Collection<?> objects) {
        return objects.stream().map(Object::toString).collect(Collectors.joining("\n"));
    }

    public static ContainerRequestFilter getContainerRequestFilter() {
        return new DebugHelperContainerRequestFilter();
    }

    public static ContainerResponseFilter getContainerResponseFilter() {
        return new DebugHelperContainerResponseFilter();
    }

    public static ExceptionMapper getExceptionMapper() {
        return new DebugHelperExceptionMapper();
    }

    @Provider
    static class DebugHelperContainerRequestFilter implements ContainerRequestFilter {
        @Override
        public void filter(ContainerRequestContext requestContext) {
            LOG.error("XXXXXX before");
        }
    }

    @Provider
    static class DebugHelperContainerResponseFilter implements ContainerResponseFilter {
        @Override
        public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
            LOG.error("XXXXXX after");
        }
    }

    @Provider
    static class DebugHelperExceptionMapper implements ExtendedExceptionMapper<Throwable> {
        @Override
        public boolean isMappable(Throwable t) {
            LOG.error("XXXXXX exception");
            return false;
        }
        @Override
        public Response toResponse(Throwable t) {
            LOG.error("should not be reachable");
            throw new RuntimeException("wack");
        }
    }

    // https://eclipse-ee4j.github.io/jersey.github.io/documentation/latest3x/monitoring_tracing.html
}
