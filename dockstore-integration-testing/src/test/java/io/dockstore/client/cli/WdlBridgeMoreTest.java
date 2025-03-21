package io.dockstore.client.cli;

import io.dockstore.common.DockerParameter;
import io.dockstore.common.WdlBridge;
import io.dockstore.webservice.core.SourceFile;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import wdl.draft3.parser.WdlParser;

public class WdlBridgeMoreTest {

    @Test
    void testBooleanMetadata() throws WdlParser.SyntaxError {
        WdlBridge wdlBridge = new WdlBridge();

        // this is problematic?
        Set<SourceFile> secondarySourceFiles = Collections.emptySet();
        final Map<String, String> pathToContentMap = secondarySourceFiles.stream()
            .collect(Collectors.toMap(SourceFile::getAbsolutePath, SourceFile::getContent));
        wdlBridge.setSecondaryFiles(new HashMap<>(pathToContentMap));

        File file = new File("/home/dyuen/warp/verification/test-wdls/TestMultiome.wdl");
        String filePath = file.getAbsolutePath();
        String sourceFilePath = "TestMultiome.wdl";
        Map<String, DockerParameter> callsToDockerMap = wdlBridge.getCallsToDockerMap(filePath, sourceFilePath);
        System.out.println("foo");
    }
}
