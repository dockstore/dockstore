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

package io.dockstore.common;

import com.google.common.base.Optional;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.builder.ConfigurationBuilder;
import org.apache.commons.configuration2.builder.ReloadingFileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.reloading.PeriodicReloadingTrigger;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xliu
 */
public final class Utilities {

    private static final Map<String, ConfigurationBuilder<INIConfiguration>> MAP = new HashMap<>();

    private static final Logger LOG = LoggerFactory.getLogger(Utilities.class);
    private static final long DEFAULT_TIMEOUT_MILLISECONDS = 0;  // if 0, do not set timeout at all
    public static final String PROBLEMS_RUNNING_COMMAND = "problems running command: {}";

    private Utilities() {
        // hide the default constructor for a utility class
    }

    /**
     * The singleton map os not entirely awesome, but this allows our legacy code to
     * benefit from live reloads for configuration while the application is running
     *
     * @param configFile the path to the config file which should be loaded
     * @return configuration file
     */
    public static INIConfiguration parseConfig(String configFile) {
        if (!MAP.containsKey(configFile)) {
            ReloadingFileBasedConfigurationBuilder<INIConfiguration> builder = new ReloadingFileBasedConfigurationBuilder<>(
                    INIConfiguration.class).configure(new Parameters().properties().setFileName(configFile));
            PeriodicReloadingTrigger trigger = new PeriodicReloadingTrigger(builder.getReloadingController(), null, 1, TimeUnit.MINUTES);
            trigger.start();
            MAP.put(configFile, builder);
        }
        try {
            return MAP.get(configFile).getConfiguration();
        } catch (ConfigurationException ex) {
            throw new RuntimeException("Could not read " + configFile);
        }
    }

    public static ImmutablePair<String, String> executeCommand(String command) {
        return executeCommand(command, null);
    }

    public static ImmutablePair<String, String> executeCommand(String command, File workingDir) {
        return executeCommand(command, workingDir, DEFAULT_TIMEOUT_MILLISECONDS);
    }

    public static ImmutablePair<String, String> executeCommand(String command, File workingDir, long timeout) {
        return executeCommand(command, true, Optional.of(ByteStreams.nullOutputStream()), Optional.of(ByteStreams.nullOutputStream()), workingDir, timeout);
    }

    public static ImmutablePair<String, String> executeCommand(String command, OutputStream stdoutStream, OutputStream stderrStream) {
        return executeCommand(command, true, Optional.of(stdoutStream), Optional.of(stderrStream), null);
    }

    public static ImmutablePair<String, String> executeCommand(String command, OutputStream stdoutStream, OutputStream stderrStream, File workingDir) {
        return executeCommand(command, true, Optional.of(stdoutStream), Optional.of(stderrStream), workingDir);
    }

    public static ImmutablePair<String, String> executeCommand(String command, OutputStream stdoutStream, OutputStream stderrStream,
            File workingDir, Map<String, String> additionalEnvironment) {
        return executeCommand(command, true, Optional.of(stdoutStream), Optional.of(stderrStream), workingDir, additionalEnvironment, DEFAULT_TIMEOUT_MILLISECONDS);
    }

    /**
     * Execute a command and return stdout and stderr
     *
     * @param command the command to execute
     * @return the stdout and stderr
     */
    private static ImmutablePair<String, String> executeCommand(String command, final boolean dumpOutput,
            Optional<OutputStream> stdoutStream, Optional<OutputStream> stderrStream, File workingDir) {
        return executeCommand(command, dumpOutput, stdoutStream, stderrStream, workingDir, null, DEFAULT_TIMEOUT_MILLISECONDS);
    }

    /**
     * Execute a command with custom timeout and return stdout and stderr
     *
     * @param command the command to execute
     * @param timeout the max time in milliseconds to wait for execution to finish
     * @return the stdout and stderr
     */
    private static ImmutablePair<String, String> executeCommand(String command, final boolean dumpOutput,
                                                                Optional<OutputStream> stdoutStream, Optional<OutputStream> stderrStream, File workingDir, long timeout) {
        return executeCommand(command, dumpOutput, stdoutStream, stderrStream, workingDir, null, timeout);
    }

    /**
     * Execute a command and return stdout and stderr
     *
     * @param command the command to execute
     * @param additionalEnvironment additional environment variables that are added to the system environment; can be null
     * @return the stdout and stderr
     */
    private static ImmutablePair<String, String> executeCommand(String command, final boolean dumpOutput,
            Optional<OutputStream> stdoutStream, Optional<OutputStream> stderrStream, File workingDir, Map<String, String> additionalEnvironment, long timeout) {
        // TODO: limit our output in case the called program goes crazy

        // these are for returning the output for use by this
        try (ByteArrayOutputStream localStdoutStream = new ByteArrayOutputStream();
                ByteArrayOutputStream localStdErrStream = new ByteArrayOutputStream()) {
            OutputStream stdout = localStdoutStream;
            OutputStream stderr = localStdErrStream;
            if (stdoutStream.isPresent()) {
                assert stderrStream.isPresent();
                // in this branch, we want a copy of the output for Consonance
                stdout = new TeeOutputStream(localStdoutStream, stdoutStream.get());
                stderr = new TeeOutputStream(localStdErrStream, stderrStream.get());
            }

            DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
            String utf8 = StandardCharsets.UTF_8.name();
            try {
                final CommandLine parse = CommandLine.parse(command);
                // When running bash commands directly, we need this to substitute variables
                parse.setSubstitutionMap(additionalEnvironment);
                Executor executor = new DefaultExecutor();
                if (workingDir != null) {
                    LOG.info("working directory is " + workingDir.toString());
                    executor.setWorkingDirectory(workingDir);
                }
                executor.setExitValue(0);
                if (dumpOutput) {
                    LOG.info("CMD: " + command);
                }
                final Map<String, String> procEnvironment = EnvironmentUtils.getProcEnvironment();
                if (additionalEnvironment != null) {
                    procEnvironment.putAll(additionalEnvironment);
                }
                // get stdout and stderr
                executor.setStreamHandler(new PumpStreamHandler(stdout, stderr));
                executor.execute(parse, procEnvironment, resultHandler);

                // wait for the duration of the timeout, unless timeout set to 0
                if (timeout == 0) {
                    resultHandler.waitFor();
                } else {
                    resultHandler.waitFor(timeout);
                }
                // not sure why commons-exec does not throw an exception
                if (resultHandler.getExitValue() != 0) {
                    LOG.error(PROBLEMS_RUNNING_COMMAND, command, resultHandler.getException());
                    throw new ExecuteException(PROBLEMS_RUNNING_COMMAND + command, resultHandler.getExitValue());
                }
                return new ImmutablePair<>(localStdoutStream.toString(utf8), localStdErrStream.toString(utf8));
            } catch (InterruptedException | IOException e) {
                throw new IllegalStateException(PROBLEMS_RUNNING_COMMAND + command, e);
            } finally {
                if (dumpOutput) {
                    LOG.info("exit code: " + resultHandler.getExitValue());
                    try {
                        LOG.debug("stderr was: " + localStdErrStream.toString(utf8));
                        LOG.debug("stdout was: " + localStdoutStream.toString(utf8));
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException("utf-8 does not exist?", e);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("could not close output streams", e);
        }
    }

    /**
     * Cleans input of characters that might break-up logging output
     * @param input
     * @return
     */
    public static String cleanForLogging(String input) {
        return input.replaceAll("[\n\r\t]", "_");
    }
}
