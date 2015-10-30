package io.github.collaboratory;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.SignerFactory;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.internal.S3Signer;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.Selectors;
import org.apache.commons.vfs2.VFS;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.composer.ComposerException;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author boconnor 9/24/15
 * @author dyuen
 * @author tetron
 */
public class LauncherCWL {

    static {
        SignerFactory.registerSigner("S3Signer", S3Signer.class);
    }

    private static final Logger LOG = LoggerFactory.getLogger(LauncherCWL.class);

    public static final String S3_ENDPOINT = "s3.endpoint";
    public static final String WORKING_DIRECTORY = "working-directory";
    public static final String DCC_CLIENT_KEY = "dcc_storage.client";
    private final String configFilePath;
    private final String imageDescriptorPath;
    private final String runtimeDescriptorPath;
    private HierarchicalINIConfiguration config = null;
    private String globalWorkingDir = null;
    private final Yaml yaml = new Yaml(new SafeConstructor());
    private final Optional<OutputStream> stdoutStream;
    private final Optional<OutputStream> stderrStream;


    /**
     * Constructor for shell-based launch
     * @param args raw arguments from the command-line
     */
    public LauncherCWL(String[] args) {
        // create the command line parser
        CommandLineParser parser = new DefaultParser();
        // parse command line
        CommandLine line = parseCommandLine(parser, args);
        this.configFilePath = line.getOptionValue("config");
        this.imageDescriptorPath = line.getOptionValue("descriptor");
        this.runtimeDescriptorPath = line.getOptionValue("job");
        // do not forward stdout and stderr
        stdoutStream = Optional.absent();
        stderrStream = Optional.absent();
    }

    /**
     * Constructor for programmatic launch
     * @param configFilePath configuration for this launcher
     * @param imageDescriptorPath descriptor for the tool itself
     * @param runtimeDescriptorPath descriptor for this run of the tool
     */
    public LauncherCWL(String configFilePath, String imageDescriptorPath, String runtimeDescriptorPath, OutputStream stdoutStream, OutputStream stderrStream){
        this.configFilePath = configFilePath;
        this.imageDescriptorPath = imageDescriptorPath;
        this.runtimeDescriptorPath = runtimeDescriptorPath;
        // programmatically forward stdout and stderr
        this.stdoutStream = Optional.of(stdoutStream);
        this.stderrStream = Optional.of(stderrStream);
    }

    public void run(){
        // now read in the INI file
        try {
            config = new HierarchicalINIConfiguration(configFilePath);
        } catch (ConfigurationException e) {
            throw new RuntimeException("could not read launcher config ini", e);
        }

        // parse the CWL tool definition without validation
        Map<String, Object> cwl = parseCWL(imageDescriptorPath, false);

        if (cwl == null) {
            LOG.info("CWL was null");
            return;
        }

        if (!(cwl.get("class")).equals("CommandLineTool")) {
            LOG.info("Must be CommandLineTool");
            return;
        }

        // this is the job parameterization, just a JSON, defines the inputs/outputs in terms or real URLs that are provisioned by the launcher
        Map<String, Map<String, Object>> inputsAndOutputsJson = loadJob(runtimeDescriptorPath);

        if (inputsAndOutputsJson == null) {
            LOG.info("Cannot load job object.");
            return;
        }

        // setup directories
        globalWorkingDir = setupDirectories();
        
        // pull input files
        final  Map<String, FileInfo> inputsId2dockerMountMap = pullFiles(cwl, inputsAndOutputsJson);

        // prep outputs, just creates output dir and records what the local output path will be
        Map<String, FileInfo> outputMap = prepUploads(cwl, inputsAndOutputsJson);

        // create updated JSON inputs document
        String newJsonPath = createUpdatedInputsAndOutputsJson(inputsId2dockerMountMap, outputMap, inputsAndOutputsJson);

        // run command
        LOG.info("RUNNING COMMAND");
        Map<String, Object> outputObj = runCWLCommand(imageDescriptorPath, newJsonPath, globalWorkingDir + "/outputs/");

        // push output files
        pushOutputFiles(outputMap, outputObj);
    }
    
    private Map<String, FileInfo> prepUploads(Map<String, Object> cwl, Map<String, Map<String, Object>> inputsOutputs) {

        Map<String, FileInfo> fileMap = new HashMap<>();

        LOG.info("PREPPING UPLOADS...");

        List<Map<String, Object>> files = (List) ((Map)cwl).get("outputs");

        // for each file input from the CWL
        for (Map<String, Object> file : files) {

            // pull back the name of the input from the CWL
            LOG.info(file.toString());
            String cwlID = ((String)((Map) file).get("id")).substring(1);
            LOG.info("ID: " + cwlID);

            // now that I have an input name from the CWL I can find it in the JSON parameterization for this run
            LOG.info("JSON: " + inputsOutputs.toString());
            for (String paramName : inputsOutputs.keySet()) {
                Map param = inputsOutputs.get(paramName);
                String path = (String)param.get("path");

                if (paramName.equals(cwlID)) {

                    // if it's the current one
                    LOG.info("PATH TO UPLOAD TO: " + path + " FOR " + cwlID + " FOR " + paramName);

                    // output
                    // TODO: poor naming here, need to cleanup the variables
                    // just file name
                    // the file URL
                    File filePathObj = new File(cwlID);
                    //String newDirectory = globalWorkingDir + "/outputs/" + UUID.randomUUID().toString();
                    String newDirectory = globalWorkingDir + "/outputs";
                    executeCommand("mkdir -p " + newDirectory);
                    File newDirectoryFile = new File(newDirectory);
                    String uuidPath = newDirectoryFile.getAbsolutePath() + "/" + filePathObj.getName();

                    // VFS call, see https://github.com/abashev/vfs-s3/tree/branch-2.3.x and
                    // https://commons.apache.org/proper/commons-vfs/filesystems.html

                    // now add this info to a hash so I can later reconstruct a docker -v command
                    FileInfo new1 = new FileInfo();
                    new1.setUrl(path);
                    new1.setDockerPath(cwlID);
                    new1.setLocalPath(uuidPath);
                    fileMap.put(cwlID, new1);

                    LOG.info("UPLOAD FILE: LOCAL: " + cwlID + " URL: " + path);
                }
            }
        }
        return fileMap;
    }

    private String createUpdatedInputsAndOutputsJson(Map<String, FileInfo> fileMap, Map<String, FileInfo> outputMap, Map<String, Map<String, Object>> inputsAndOutputsJson) {

        JSONObject newJSON = new JSONObject();

        for (String paramName : inputsAndOutputsJson.keySet()) {
            Map<String, Object> param = inputsAndOutputsJson.get(paramName);
            String path = (String) param.get("path");
            LOG.info("PATH: " + path + " PARAM_NAME: " + paramName);
            // will be null for output
            if (fileMap.get(paramName) != null) {
                final String localPath = fileMap.get(paramName).getLocalPath();
                param.put("path", localPath);
                LOG.info("NEW FULL PATH: " + localPath);
            } else if (outputMap.get(paramName) != null) {
                final String localPath = outputMap.get(paramName).getLocalPath();
                param.put("path", localPath);
                LOG.info("NEW FULL PATH: " + localPath);
            }
            // now add to the new JSON structure
            JSONObject newRecord = new JSONObject();
            newRecord.put("class", param.get("class"));
            newRecord.put("path", param.get("path"));
            newJSON.put(paramName, newRecord);
        }

        writeJob("foo.json", newJSON);

        return("foo.json");
    }

    private Map<String, Map<String, Object>> loadJob(String jobPath) {
        try {
            return (Map<String, Map<String, Object>>)yaml.load(new FileInputStream(jobPath));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("could not load job from yaml",e);
        }
    }

    private void writeJob(String jobOutputPath, JSONObject newJson) {
        try {
            //TODO: investigate, why is this replacement occurring?
            final String replace = newJson.toJSONString().replace("\\", "");
            FileUtils.writeStringToFile(new File(jobOutputPath), replace, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not write job ", e);
        }
    }

    private String setupDirectories() {

        LOG.info("MAKING DIRECTORIES...");
        // directory to use, typically a large, encrypted filesystem
        String workingDir = config.getString(WORKING_DIRECTORY, "./datastore/");
        // make UUID
        UUID uuid = UUID.randomUUID();
        // setup directories
        globalWorkingDir = workingDir + "/launcher-" + uuid.toString();
        executeCommand("mkdir -p " + workingDir + "/launcher-" + uuid.toString());
        executeCommand("mkdir -p " + workingDir + "/launcher-" + uuid.toString() + "/configs");
        executeCommand("mkdir -p " + workingDir + "/launcher-" + uuid.toString() + "/working");
        executeCommand("mkdir -p " + workingDir + "/launcher-" + uuid.toString() + "/inputs");
        executeCommand("mkdir -p " + workingDir + "/launcher-" + uuid.toString() + "/logs");
        executeCommand("mkdir -p " + workingDir + "/launcher-" + uuid.toString() + "/outputs");

        return (new File(workingDir + "/launcher-" + uuid.toString()).getAbsolutePath());
    }

    private Map<String, Object> runCWLCommand(String cwlFile, String jsonSettings, String workingDir) {
        String[] s = new String[]{"cwltool","--outdir", workingDir, cwlFile, jsonSettings};
        final ImmutablePair<String, String> execute = this.executeCommand(Joiner.on(" ").join(Arrays.asList(s)));
        Map<String, Object> obj = (Map<String, Object>)yaml.load(execute.getLeft());
        return obj;
    }

    private void pushOutputFiles(Map<String, FileInfo> fileMap, Map<String, Object> outputObject) {

        LOG.info("UPLOADING FILES...");

        for (String fileName : fileMap.keySet()) {
            FileInfo file = fileMap.get(fileName);

            String cwlOutputPath = (String)((Map)((Map)outputObject).get(fileName)).get("path");

            LOG.info("NAME: " + file.getLocalPath() + " URL: " + file.getUrl() + " FILENAME: " + fileName + " CWL OUTPUT PATH: "
                    + cwlOutputPath);

            if (file.getUrl().startsWith("s3://")) {
                AmazonS3 s3Client = new AmazonS3Client(new ClientConfiguration().withSignerOverride("S3Signer"));
                if (config.containsKey(LauncherCWL.S3_ENDPOINT)){
                    final String endpoint = config.getString(LauncherCWL.S3_ENDPOINT);
                    LOG.info("found custom S3 endpoint, setting to " + endpoint);
                    s3Client.setEndpoint(endpoint);
                    s3Client.setS3ClientOptions(new S3ClientOptions().withPathStyleAccess(true));
                }
                String trimmedPath = file.getUrl().replace("s3://","");
                List<String> splitPathList  = Lists.newArrayList(trimmedPath.split("/"));
                String bucketName = splitPathList.remove(0);

                s3Client.putObject(new PutObjectRequest(bucketName, Joiner.on("/").join(splitPathList), new File(cwlOutputPath)));
            } else {

                try {
                    FileSystemManager fsManager;
                    // trigger a copy from the URL to a local file path that's a UUID to avoid collision
                    fsManager = VFS.getManager();
                    FileObject dest = fsManager.resolveFile(file.getUrl());
                    FileObject src = fsManager.resolveFile(new File(cwlOutputPath).getAbsolutePath());
                    dest.copyFrom(src, Selectors.SELECT_SELF);
                } catch (FileSystemException e) {
                    throw new RuntimeException("Could not provision output files", e);
                }
            }
        }
    }

    /**
     * Execute a command and return stdout and stderr
     * @param command the command to execute
     * @return the stdout and stderr
     */
    private ImmutablePair<String, String> executeCommand(String command) {
        LOG.info("CMD: " + command);
        // TODO: limit our output in case the called program goes crazy

        // these are for returning the output for use by this
        ByteArrayOutputStream localStdoutStream = new ByteArrayOutputStream();
        ByteArrayOutputStream localStdErrStream = new ByteArrayOutputStream();
        OutputStream stdout =  localStdoutStream;
        OutputStream stderr = localStdErrStream;
        if (this.stdoutStream.isPresent()){
            assert(this.stderrStream.isPresent());
            // in this branch, we want a copy of the output for Consonance
            stdout = new TeeOutputStream(localStdoutStream, this.stdoutStream.get());
            stderr = new TeeOutputStream(localStdErrStream, this.stderrStream.get());
        }

        DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
        String utf8 = StandardCharsets.UTF_8.name();
        try {
            final org.apache.commons.exec.CommandLine parse = org.apache.commons.exec.CommandLine.parse(command);
            Executor executor = new DefaultExecutor();
            executor.setExitValue(0);
            System.out.println("executor working directory: " + executor.getWorkingDirectory().getAbsolutePath());
            // get stdout and stderr
            executor.setStreamHandler(new PumpStreamHandler(stdout, stderr));
            executor.execute(parse, resultHandler);
            resultHandler.waitFor();
            // not sure why commons-exec does not throw an exception
            if (resultHandler.getExitValue() != 0) {
            	resultHandler.getException().printStackTrace();
                throw new ExecuteException("could not run command: " + command, resultHandler.getExitValue());
            }
            return new ImmutablePair<>(localStdoutStream.toString(utf8), localStdErrStream.toString(utf8));
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException("could not run command: " + command, e);
        } finally {
            LOG.info("exit code: " + resultHandler.getExitValue());
            try {
                LOG.info("stderr was: " + localStdErrStream.toString(utf8));
                LOG.info("stdout was: " + localStdoutStream.toString(utf8));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("utf-8 does not exist?", e);
            }
         }
    }
    
    private String getStorageClient() {
    	return config.getString(DCC_CLIENT_KEY, "/icgc/dcc-storage/bin/dcc-storage-client");
    }
    
    private void downloadFromDccStorage(String objectId, String downloadDir) {  	
    	// default layout saves to original_file_name/object_id
    	// file name is the directory and object id is actual file name
    	String client = getStorageClient();
    	StringBuilder bob = new StringBuilder(client).append(" --quiet");
        bob.append(" download");
    	bob.append(" --object-id ").append(objectId);
    	bob.append(" --output-dir ").append(downloadDir);
    	bob.append(" --output-layout id");
    	executeCommand(bob.toString());
    }
    
    private Map<String, FileInfo> pullFiles(Map<String, Object> cwl, Map<String, Map<String, Object>> inputsOutputs) {
        Map<String, FileInfo> fileMap = new HashMap<>();

        LOG.info("DOWNLOADING INPUT FILES...");

        List<Map<String, Object>> files = (List) cwl.get("inputs");

        // for each file input from the CWL
        for (Map<String, Object> file : files) {

            // pull back the name of the input from the CWL
            LOG.info(file.toString());
            // remove the hash from the cwlInputFileID
            String cwlInputFileID = ((String)file.get("id")).substring(1);
            LOG.info("ID: " + cwlInputFileID);

            // now that I have an input name from the CWL I can find it in the JSON parameterization for this run
            LOG.info("JSON: " + inputsOutputs.toString());
            for (String paramName : inputsOutputs.keySet()) {
                HashMap param = (HashMap)inputsOutputs.get(paramName);
                String path = (String)param.get("path");

                if (paramName.equals(cwlInputFileID)) {
                    // if it's the current one
                    LOG.info("PATH TO DOWNLOAD FROM: " + path + " FOR " + cwlInputFileID + " FOR " + paramName);

                    // set up output paths                   
                    String downloadDirectory = globalWorkingDir + "/inputs/" + UUID.randomUUID().toString();
                    executeCommand("mkdir -p " + downloadDirectory);
                    File downloadDirFileObj = new File(downloadDirectory);
                    
                    String targetFilePath = downloadDirFileObj.getAbsolutePath() + "/" + cwlInputFileID;
                    
                    // expects URI in "path": "icgc:eef47481-670d-4139-ab5b-1dad808a92d9"
                    PathInfo pathInfo = new PathInfo(path);
                    if (pathInfo.isObjectIdType()) {
                        String objectId = pathInfo.getObjectId();
                        downloadFromDccStorage(objectId, downloadDirectory);

                        // downloaded file
                        String downloadPath = downloadDirFileObj.getAbsolutePath() + "/" + objectId;
                        System.out.println("download path: " + downloadPath);
                        File downloadedFileFileObj = new File(downloadPath);
                        File targetPathFileObj = new File(targetFilePath);
                        try {
                            Files.move(downloadedFileFileObj, targetPathFileObj);
                        } catch (IOException ioe) {
                            LOG.error(ioe.getMessage());
                            throw new RuntimeException("Could not move input file: ", ioe);
                        }
                    } else if (path.startsWith("s3://")) {
                        AmazonS3 s3Client = new AmazonS3Client(new ClientConfiguration().withSignerOverride("S3Signer"));
                        if (config.containsKey(LauncherCWL.S3_ENDPOINT)){
                            final String endpoint = config.getString(LauncherCWL.S3_ENDPOINT);
                            LOG.info("found custom S3 endpoint, setting to " + endpoint);
                            s3Client.setEndpoint(endpoint);
                            s3Client.setS3ClientOptions(new S3ClientOptions().withPathStyleAccess(true));
                        }
                        String trimmedPath = path.replace("s3://","");
                        List<String> splitPathList  = Lists.newArrayList(trimmedPath.split("/"));
                        String bucketName = splitPathList.remove(0);

                        S3Object object = s3Client.getObject(
                                new GetObjectRequest(bucketName, Joiner.on("/").join(splitPathList)));
                        try {
                            FileUtils.copyInputStreamToFile(object.getObjectContent(), new File(targetFilePath));
                        } catch (IOException e) {
                            throw new RuntimeException("Could not provision input files from S3", e);
                        }
                    } else {
                        // VFS call, see https://github.com/abashev/vfs-s3/tree/branch-2.3.x and
                        // https://commons.apache.org/proper/commons-vfs/filesystems.html
                        FileSystemManager fsManager;
                        try {
                            // trigger a copy from the URL to a local file path that's a UUID to avoid collision
                            fsManager = VFS.getManager();
                            FileObject src = fsManager.resolveFile(path);
                        FileObject dest = fsManager.resolveFile(new File(targetFilePath).getAbsolutePath());
                            dest.copyFrom(src, Selectors.SELECT_SELF);
                        } catch (FileSystemException e) {
                            LOG.error(e.getMessage());
                            throw new RuntimeException("Could not provision input files", e);
                        }
                    }
                    // now add this info to a hash so I can later reconstruct a docker -v command
                    FileInfo info = new FileInfo();
                        info.setLocalPath(targetFilePath);
                    info.setLocalPath(targetFilePath);
                    info.setDockerPath(cwlInputFileID);
                    info.setUrl(path);

                    fileMap.put(cwlInputFileID, info);
                    LOG.info("DOWNLOADED FILE: LOCAL: " + cwlInputFileID + " URL: " + path);
                }
            }
        }
        return fileMap;
    }


    private Map<String, Object> parseCWL(String cwlFile, boolean validate) {
        try {
            // update seems to just output the JSON version without checking file links
            String[] s = new String[]{"cwltool", validate ? "--print-pre" : "--update", cwlFile };
            final ImmutablePair<String, String> execute = this.executeCommand(Joiner.on(" ").join(Arrays.asList(s)));
            Map<String, Object> obj = (Map<String, Object>)yaml.load(execute.getLeft());
            return obj;
        } catch (ComposerException e){
            throw new RuntimeException("Must be single object at root", e);
        }
    }

    private CommandLine parseCommandLine(CommandLineParser parser, String[] args) {
        try {
            // parse the command line arguments
            Options options = new Options();
            options.addOption("c", "config", true, "the INI config file for this tool");
            options.addOption("d", "descriptor", true, "a CWL tool descriptor used to construct the command and run it");
            options.addOption("j", "job", true, "a JSON parameterization of the CWL tool, includes URLs for inputs and outputs");
            return parser.parse(options, args);
        } catch (ParseException exp) {
            LOG.error("Unexpected exception:" + exp.getMessage());
            throw new RuntimeException("Could not parse command-line", exp);
        }
    }

    public static class PathInfo {
        private static final Logger LOG = LoggerFactory.getLogger(PathInfo.class);
        public static final String DCC_STORAGE_SCHEME = "icgc";
    	private boolean objectIdType = false;
    	private String objectId = "";
    	
		public boolean isObjectIdType() {
			return objectIdType;
		}

		public String getObjectId() {
			return objectId;
		}
		
		public PathInfo(String path) {
			super();
			try {
		    	URI objectIdentifier = URI.create(path);	// throws IllegalArgumentException if it isn't a valid URI
		    	if (objectIdentifier.getScheme().equalsIgnoreCase(DCC_STORAGE_SCHEME)) {
		    		objectIdType = true;
		    		objectId = objectIdentifier.getSchemeSpecificPart().toLowerCase();
		    	}				
			} catch (IllegalArgumentException iae) {
				StringBuilder bob = new StringBuilder("Invalid path specified for CWL pre-processor values: ").append(path);
				LOG.warn(bob.toString());
				objectIdType = false;
			}
		}
    }
    
    public static class FileInfo {
        private String localPath;
        private String dockerPath;
        private String url;
        private String defaultLocalPath;

        public String getLocalPath() {
            return localPath;
        }

        public void setLocalPath(String localPath) {
            this.localPath = localPath;
        }

        public String getDockerPath() {
            return dockerPath;
        }

        public void setDockerPath(String dockerPath) {
            this.dockerPath = dockerPath;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getDefaultLocalPath() {
            return defaultLocalPath;
        }

        public void setDefaultLocalPath(String defaultLocalPath) {
            this.defaultLocalPath = defaultLocalPath;
        }
    }


    public static void main(String[] args) {
        final LauncherCWL launcherCWL = new LauncherCWL(args);
        launcherCWL.run();
    }
}
