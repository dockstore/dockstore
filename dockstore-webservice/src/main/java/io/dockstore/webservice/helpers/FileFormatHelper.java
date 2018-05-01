package io.dockstore.webservice.helpers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.dockstore.webservice.core.FileFormat;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.jdbi.FileFormatDAO;
import io.dockstore.webservice.languages.CWLHandler;

/**
 * This class helps resolve file formats in versions
 * @author gluu
 * @since 1.5.0
 */
public final class FileFormatHelper {
    private FileFormatHelper() { }

    /**
     * Updates the given tool/workflow to show which file formats are associated with its sourcefiles
     * @param versions  A tool/workflow's versions (tags/workflowVersions)
     * @param fileFormatDAO  The FileFormatDAO to check the FileFormat table
     */
    public static void updateFileFormats(Set<? extends Version> versions, final FileFormatDAO fileFormatDAO) {
        Set<? extends Version> tags = versions;
        CWLHandler cwlHandler = new CWLHandler();
        tags.forEach(tag -> {
            Set<FileFormat> inputFileFormats = new HashSet<>();
            Set<FileFormat> outputFileFormats = new HashSet<>();
            Set<SourceFile> sourceFiles = tag.getSourceFiles();
            List<SourceFile> cwlFiles = sourceFiles.stream()
                    .filter(sourceFile -> sourceFile.getType().equals(SourceFile.FileType.DOCKSTORE_CWL)).collect(Collectors.toList());
            cwlFiles.forEach(cwlFile -> {
                inputFileFormats.addAll(cwlHandler.getFileFormats(cwlFile.getContent(), "inputs"));
                outputFileFormats.addAll(cwlHandler.getFileFormats(cwlFile.getContent(), "outputs"));
                inputFileFormats.addAll(outputFileFormats);
            });
            Set<FileFormat> realFileFormats = new HashSet<>();

            inputFileFormats.forEach(fileFormat -> {
                FileFormat fileFormatFromDB = fileFormatDAO.findByLabelValue(fileFormat.getValue());
                if (fileFormatFromDB != null) {
                    realFileFormats.add((fileFormatFromDB));
                } else {
                    fileFormatFromDB = new FileFormat();
                    fileFormatFromDB.setValue(fileFormat.getValue());
                    String id = fileFormatDAO.create(fileFormatFromDB);
                    realFileFormats.add(fileFormatDAO.findByLabelValue(id));
                }
            });
            tag.setFileFormats(realFileFormats);
        });
    }
}
