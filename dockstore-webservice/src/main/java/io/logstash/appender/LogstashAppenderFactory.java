/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.logstash.appender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.dropwizard.logging.AbstractAppenderFactory;
import io.dropwizard.logging.async.AsyncAppenderFactory;
import io.dropwizard.logging.filter.LevelFilterFactory;
import io.dropwizard.logging.layout.LayoutFactory;
import java.net.InetSocketAddress;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import net.logstash.logback.appender.LogstashTcpSocketAppender;
import net.logstash.logback.encoder.LogstashEncoder;

/**
 * Custom log appender that pushes the logs to logstash.
 * Specify the host and optional port in the application configuration file.
 */
@JsonTypeName("logstash")
public class LogstashAppenderFactory extends AbstractAppenderFactory {
    private static final int MAX_PORT = 65535;
    private static final int MIN_PORT = 1;

    @NotNull
    protected String host;

    @Min(MIN_PORT)
    @Max(MAX_PORT)
    protected int port;

    @Min(MIN_PORT)
    @Max(MAX_PORT)
    public LogstashAppenderFactory() {
        this.port = LogstashTcpSocketAppender.DEFAULT_PORT;
    }

    @JsonProperty
    public String getHost() {
        return host;
    }

    @JsonProperty
    public void setHost(String host) {
        this.host = host;
    }

    @JsonProperty
    public int getPort() {
        return port;
    }

    @JsonProperty
    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public Appender build(LoggerContext context, String s, LayoutFactory layoutFactory, LevelFilterFactory levelFilterFactory,
            AsyncAppenderFactory asyncAppenderFactory) {
        final LogstashTcpSocketAppender appender = new LogstashTcpSocketAppender();
        final LogstashEncoder encoder = new LogstashEncoder();

        encoder.setIncludeContext(true);

        // Mapped Diagnostic Context
        encoder.setIncludeMdc(true);
        encoder.setIncludeCallerData(false);

        appender.setContext(context);
        appender.addDestinations(new InetSocketAddress(host, port));
        appender.setIncludeCallerData(false);
        appender.setQueueSize(LogstashTcpSocketAppender.DEFAULT_QUEUE_SIZE);
        appender.addFilter(levelFilterFactory.build(Level.ALL));
        appender.setEncoder(encoder);

        encoder.start();
        appender.start();

        return wrapAsync(appender, asyncAppenderFactory);
    }
}
