package io.dockstore.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import io.dockstore.common.cwl.Any;
import io.dockstore.common.cwl.CommandInputParameter;
import io.dockstore.common.cwl.CommandLineTool;
import io.dockstore.common.cwl.CommandOutputParameter;

/**
 * Helper class that performs utility functions relating to CWL parsing and manipulation.
 * @author dyuen
 */
public class CWL {

    private final Gson gson;
    private Logger log = LoggerFactory.getLogger(CWL.class);

    public CWL(){
        gson =  getTypeSafeCWLToolDocument();
    }

    /**
     * Convert a String representation of a CWL file to a run json
     * @param output
     * @return
     */
    public Map<String, Object> extractRunJson(final String output) {
        final CommandLineTool commandLineTool = gson.fromJson(output, CommandLineTool.class);
        final Map<String, Object> runJson = new HashMap<>();
        final List<CommandInputParameter> inputs = commandLineTool.getInputs();
        final List<CommandOutputParameter> outputs = commandLineTool.getOutputs();

        for(final CommandInputParameter inputParam : inputs){
            final String idString = inputParam.getId().toString();
            final Object stub = getStub(inputParam.getType());
            runJson.put(idString.substring(idString.lastIndexOf('#') + 1), stub);
        }
        for(final CommandOutputParameter outParam : outputs){
            final String idString = outParam.getId().toString();
            final Object stub = getStub(outParam.getType());
            runJson.put(idString.substring(idString.lastIndexOf('#') + 1), stub);
        }
        return runJson;
    }

    /**
     * This is an ugly mapping between CWL's primitives and Java primitives
     * @param type
     * @return
     */
    private static Object getStub(Object type) {
        Object stub = "fill me in";
        String strType = type.toString();
        switch (strType) {
        case "File":
            Map<String, String> file = new HashMap<>();
            file.put("class", "File");
            file.put("path", "fill me in");
            stub = file;
            break;
        case "boolean":
            stub = Boolean.FALSE;
            break;
        case "int":
            stub = 0;
            break;
        case "long":
            stub = 0L;
            break;
        case "float":
            stub = 0.0;
            break;
        case "double":
            stub = Double.MAX_VALUE;
        default:
            break;
        }
        return stub;
    }

    /**
     * @return a gson instance that can properly convert CWL tools into a typesafe Java object
     */
    public static Gson getTypeSafeCWLToolDocument() {
        final Type hintType = new TypeToken<List<Any>>() {}.getType();
        final Gson sequenceSafeGson = new GsonBuilder().registerTypeAdapter(CharSequence.class,
            (JsonDeserializer<CharSequence>) (json, typeOfT, context) -> json.getAsString()).create();

        return new GsonBuilder().registerTypeAdapter(CharSequence.class,
            (JsonDeserializer<CharSequence>) (json, typeOfT, context) -> json.getAsString())
                        .registerTypeAdapter(hintType, (JsonDeserializer) (json, typeOfT, context) -> {
                            Collection<Object> hints = new ArrayList<>();
                            for (final JsonElement jsonElement : json.getAsJsonArray()) {
                                final Object o = getCWLObject(sequenceSafeGson, jsonElement);
                                hints.add(o);
                            }
                            return hints;
                        })
                   .registerTypeAdapter(CommandInputParameter.class, (JsonDeserializer<CommandInputParameter>) (json, typeOfT, context) -> {
                       final CommandInputParameter commandInputParameter = sequenceSafeGson.fromJson(json,
                           CommandInputParameter.class);
                       // default has a dollar sign in the schema but not in sample jsons, we could do something here if we wanted
                       return commandInputParameter;
                   })
                   .serializeNulls().setPrettyPrinting()
            .create();
    }

    private static Object getCWLObject(Gson gson1, JsonElement jsonElement) {
        final String elementClass = jsonElement.getAsJsonObject().get("class").getAsString();
        Class<SpecificRecordBase> anyClass;
        try {
            anyClass = (Class<SpecificRecordBase>) Class.forName("io.dockstore.common.cwl." + elementClass);
        } catch (ClassNotFoundException e) {
            //TODO: this should be a log
            e.printStackTrace();
            anyClass = null;
        }
        return gson1.fromJson(jsonElement, anyClass);
    }

    /**
     * Execute a command and return stdout and stderr
     * @param command the command to execute
     * @return the stdout and stderr
     */
    private ImmutablePair<String, String> executeCommand(String command) {

        log.info("CMD: " + command);
        // TODO: limit our output in case the called program goes crazy

        // these are for returning the output for use by this
        ByteArrayOutputStream localStdoutStream = new ByteArrayOutputStream();
        ByteArrayOutputStream localStdErrStream = new ByteArrayOutputStream();

        DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
        String utf8 = StandardCharsets.UTF_8.name();
        try {
            final CommandLine parse = CommandLine.parse(command);
            Executor executor = new DefaultExecutor();
            executor.setExitValue(0);
            log.info("CMD: " + command);
            // get stdout and stderr
            executor.setStreamHandler(new PumpStreamHandler(localStdoutStream, localStdErrStream));
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
            log.info("exit code: " + resultHandler.getExitValue());
            try {
                log.info("stderr was: " + localStdErrStream.toString(utf8));
                log.info("stdout was: " + localStdoutStream.toString(utf8));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("utf-8 does not exist?", e);
            }
        }
    }

    public ImmutablePair<String, String> parseCWL(String cwlFile, boolean validate) {
        // update seems to just output the JSON version without checking file links
        String[] s = new String[] { "cwltool", validate ? "--print-pre" : "--update", cwlFile };
        final ImmutablePair<String, String> execute = executeCommand(Joiner.on(" ").join(Arrays.asList(s)));
        return execute;
    }
}
