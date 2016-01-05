/*
 * Copyright (C) 2015 Collaboratory
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.dockstore.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

/**
 *
 * @author xliu
 */
public class Utilities {

    private static final Logger LOG = LoggerFactory.getLogger(Utilities.class);


    public static HierarchicalINIConfiguration parseConfig(String path) {
        try {
            return new HierarchicalINIConfiguration(path);
        } catch (ConfigurationException ex) {
            throw new RuntimeException("Could not read ~/.dockstore/config");
        }
    }

    public static ImmutablePair<String, String> executeCommand(String command, Optional<OutputStream> stdoutStream, Optional<OutputStream> stderrStream) {
        return executeCommand(command, true, stdoutStream, stderrStream);
    }

    /**
     * Execute a command and return stdout and stderr
     * @param command the command to execute
     * @return the stdout and stderr
     */
    public static ImmutablePair<String, String> executeCommand(String command, final boolean dumpOutput, Optional<OutputStream> stdoutStream, Optional<OutputStream> stderrStream) {
        // TODO: limit our output in case the called program goes crazy

        // these are for returning the output for use by this
        try (ByteArrayOutputStream localStdoutStream = new ByteArrayOutputStream();
                ByteArrayOutputStream localStdErrStream = new ByteArrayOutputStream()
        ) {
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
                Executor executor = new DefaultExecutor();
                executor.setExitValue(0);
                if (dumpOutput) {
                    System.out.println("CMD: " + command);
                }
                // get stdout and stderr
                executor.setStreamHandler(new PumpStreamHandler(stdout, stderr));
                executor.execute(parse, resultHandler);
                resultHandler.waitFor();
                // not sure why commons-exec does not throw an exception
                if (resultHandler.getExitValue() != 0) {
                    resultHandler.getException().printStackTrace();
                    throw new ExecuteException("problems running command: " + command, resultHandler.getExitValue());
                }
                return new ImmutablePair<>(localStdoutStream.toString(utf8), localStdErrStream.toString(utf8));
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException("problems running command: " + command, e);
            } finally {
                if (dumpOutput) {
                    System.out.println("exit code: " + resultHandler.getExitValue());
                    try {
                        System.err.println("stderr was: " + localStdErrStream.toString(utf8));
                        System.out.println("stdout was: " + localStdoutStream.toString(utf8));
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException("utf-8 does not exist?", e);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("could not close output streams", e);
        }
    }
}
