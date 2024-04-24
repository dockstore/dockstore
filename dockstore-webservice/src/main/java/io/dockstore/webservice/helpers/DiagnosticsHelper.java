/*
 * Copyright 2024 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.helpers;

import com.codahale.metrics.Gauge;
import io.dockstore.common.Utilities;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.User;
import io.dropwizard.core.setup.Environment;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
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
import org.apache.commons.codec.digest.DigestUtils;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.stat.SessionStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public final class DiagnosticsHelper {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticsHelper.class);
    private static final double MILLISECONDS_PER_SECOND = 1e3;
    private static final double NANOSECONDS_PER_SECOND = 1e9;
    private static final double BYTES_PER_MEGABYTE = 1e6;
    private static final String RESOURCE_METHOD_PROPERTY_NAME = "io.dockstore.webservice.helpers.DiagnosticsHelper.resourceMethod";
    private static final int SEVEN = 7;

    private Environment environment;
    private SessionFactory sessionFactory;
    private DockstoreWebserviceConfiguration.DiagnosticsConfig config;

    private Logger logger;
    private MemoryMXBean memoryMXBean;
    private ThreadMXBean threadMXBean;

    public DiagnosticsHelper() {
        this(LOG);
    }

    public DiagnosticsHelper(Logger logger) {
        this.logger = logger;
        // Initialize the beans that we know are singletons, just in case there are performance issues with repeatedly retrieving them.
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
    }

    public void start(Environment newEnvironment, SessionFactory newSessionFactory, DockstoreWebserviceConfiguration.DiagnosticsConfig newConfig) {

        environment = newEnvironment;
        sessionFactory = newSessionFactory;
        config = newConfig;

        if (config.getLogPeriodic()) {
            // Create a daemon-thread-backed Timer and schedule a periodic dump of the global information.
            long periodSeconds = config.getPeriodSeconds();
            long periodMilliseconds = Math.round(periodSeconds * MILLISECONDS_PER_SECOND);
            new Timer("diagnostics", true).scheduleAtFixedRate(
                new TimerTask() {
                    public void run() {
                        logGlobals();
                    }
                }, periodMilliseconds, periodMilliseconds);
            LOG.info(String.format("logging diagnostic information every %d seconds", periodSeconds));
        }

        if (config.getLogRequests()) {
            // Register a Jersey event handler that will log session and thread-related information.
            environment.jersey().register(new DiagnosticsHelperApplicationEventListener());
            // Register a Jersey filter to determine the resource method.
            environment.jersey().register(new DiagnosticsHelperContainerRequestFilter());
            LOG.info("logging diagnostic request information");
        }
    }

    public void logGlobals() {
        logFilesystems();
        logDatabase();
        logMemory();
    }

    public void logThreads() {
        log(Level.DEBUG, "threads", () -> formatThreads());
    }

    public void logFilesystems() {
        log(Level.DEBUG, "filesystems", () -> formatFilesystems());
    }

    public void logDatabase() {
        log(Level.DEBUG, "database", () -> formatDatabase());
    }

    public void logMemory() {
        log(Level.DEBUG, "memory", () -> formatMemory());
    }

    public void logStart(ContainerRequest request) {
        log(Level.INFO, "start", () -> formatRequest(request));
    }

    public void logFinish(ContainerRequest request, ContainerResponse response, ThreadState startThreadState, ThreadState finishThreadState, Optional<SessionState> sessionState, Optional<User> user, Optional<Method> resourceMethod) {
        log(Level.INFO, "finish", () -> formatRequest(request)
            + formatUser(user)
            + formatResourceMethod(resourceMethod)
            + formatResponse(response)
            + formatThread(startThreadState, finishThreadState)
            + formatSession(sessionState));
    }

    public void log(Level level, String type, Supplier<String> valueSupplier) {
        Thread current = Thread.currentThread();
        Supplier<String> messageSupplier = () -> String.format("diagnostics.%s by thread \"%s\" (%s):\n%s", type, current.getName(), current.getName(), valueSupplier.get());
        logger.atLevel(level).log(messageSupplier);
    }

    public String formatThreads() {
        return concat(Arrays.asList(threadMXBean.dumpAllThreads(true, true)));
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

    private String formatMemoryPoolMXBean(MemoryPoolMXBean pool) {
        return nameValue("POOL", pool.getName() + ", " + pool.getType())
            + nameValue("current", pool.getUsage())
            + nameValue("peak", pool.getPeakUsage())
            + nameValue("collection", pool.getCollectionUsage());
    }

    public String formatBytes(long bytes) {
        return String.format("%.2f MB", bytes / BYTES_PER_MEGABYTE);
    }

    public String formatSession(Optional<SessionState> sessionState) {
        return nameValue("session-statistics", sessionState.orElse(null));
    }

    public String formatResourceMethod(Optional<Method> resourceMethod) {
        return nameValue("resource-method", resourceMethod.map(Method::toGenericString).orElse(null));
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

    private String formatUser(Optional<User> user) {
        return nameValue("user", user.orElse(null));
    }

    private String formatRequest(ContainerRequest request) {
        return nameValue("request.method", request.getMethod())
            + nameValue("request.url", request.getRequestUri())
            + nameValue("request.x-session-id-fingerprint", fingerprint(request.getRequestHeader("x-session-id"), SEVEN))
            + nameValue("request.x-real-ip", request.getRequestHeader("X-Real-IP"))
            + nameValue("request.user-agent", request.getRequestHeader("User-Agent"));
    }

    private String formatResponse(ContainerResponse response) {
        return nameValue("response.status", response.getStatus());
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

    private String outputFromCommand(String command) {
        return Utilities.executeCommand(command).getLeft();
    }

    private String fingerprint(Object s, int length) {
        if (s == null) {
            return null;
        }
        return DigestUtils.sha256Hex(s.toString()).substring(0, length);
    }

    private ThreadState getThreadState() {
        long bytes = 0;
        if (threadMXBean instanceof com.sun.management.ThreadMXBean sunBean) {
            bytes = sunBean.getCurrentThreadAllocatedBytes();
        } else {
            LOG.info("com.sun.management.threadMXBean not available");
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

    private Optional<SessionState> getSessionState() {
        try {
            // This getCurrentSession() call will throw if there's no current session.
            Session session = sessionFactory.getCurrentSession();
            SessionStatistics statistics = session.getStatistics();
            return Optional.of(new SessionState(statistics.getEntityCount(), statistics.getCollectionCount()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // https://eclipse-ee4j.github.io/jersey.github.io/documentation/latest3x/monitoring_tracing.html
    public class DiagnosticsHelperApplicationEventListener implements ApplicationEventListener {
        @Override
        public void onEvent(ApplicationEvent event) {
            // This space intentionally left blank.
        }

        @Override
        public RequestEventListener onRequest(RequestEvent event) {
            DiagnosticsHelperRequestEventListener listener = new DiagnosticsHelperRequestEventListener();
            listener.onEvent(event);
            return listener;
        }
    }

    public class DiagnosticsHelperRequestEventListener implements RequestEventListener {

        private ThreadState startThreadState;
        private Optional<SessionState> sessionState = Optional.empty();
        private Optional<User> user = Optional.empty();
        private Optional<Method> resourceMethod = Optional.empty();

        public DiagnosticsHelperRequestEventListener() {
            startThreadState = getThreadState();
        }

        @Override
        public void onEvent(RequestEvent event) {
            try {
                ContainerRequest request = event.getContainerRequest();
                ContainerResponse response = event.getContainerResponse();

                // Try to save some information if we haven't already.
                if (!user.isPresent() && request.getSecurityContext().getUserPrincipal() instanceof User requestUser) {
                    user = Optional.of(requestUser);
                }
                if (!resourceMethod.isPresent() && request.getProperty(RESOURCE_METHOD_PROPERTY_NAME) instanceof Method requestResourceMethod) {
                    resourceMethod = Optional.of(requestResourceMethod);
                }

                // Handle the event.
                switch (event.getType()) {

                // Request started.
                case START:
                    logStart(request);
                    break;

                // Done filtering response.
                case RESP_FILTERS_FINISHED:
                    // Experimentally, we've determined that soon after the RESP_FILTERS_FINISHED event fires, the Hibernate session is dissociated from the thread.
                    // If the session was closed during the request, it may not even be available here.
                    sessionState = getSessionState();
                    break;

                // Request finished.
                case FINISHED:
                    logFinish(request, response, startThreadState, getThreadState(), sessionState, user, resourceMethod);
                    break;

                // Do nothing for other events.
                default:
                    break;
                }
            } catch (Exception e) {
                // An Exception thrown by this handler will cause the request to fail, so we catch and suppress it.
                LOG.error("exception thrown in DiagnosticsHelperRequestEventListener.onEvent", e);
            }
        }
    }

    @Provider
    public class DiagnosticsHelperContainerRequestFilter implements ContainerRequestFilter {
        @Context
        ResourceInfo resourceInfo;

        @Override
        public void filter(ContainerRequestContext requestContext) {
            // Save the resource Method in the request context, so we can later extract and log it.
            requestContext.setProperty(RESOURCE_METHOD_PROPERTY_NAME, resourceInfo.getResourceMethod());
        }
    }

    private static record ThreadState (long allocatedBytes, long cpuTime, long userTime, long wallClock) {
    }

    private static record SessionState (long entityCount, long collectionCount) {
    }
}
