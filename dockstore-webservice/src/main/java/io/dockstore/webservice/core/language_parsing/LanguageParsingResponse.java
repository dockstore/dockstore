package io.dockstore.webservice.core.language_parsing;

import io.dockstore.common.VersionTypeValidation;
import io.dockstore.webservice.core.ParsedInformation;
import io.dockstore.webservice.core.SourceFile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Response from the external lambda parsing service")
public class LanguageParsingResponse {

    private String clonedRepositoryAbsolutePath;
    private VersionTypeValidation versionTypeValidation;
    private List<String> secondaryFilePaths;
    private LanguageParsingRequest languageParsingRequest;

    @Schema(description = "Author found from parsing the version (may possibly be different from what will be stored in Dockstore")
    private String author;
    @Schema(description = "Email found from parsing the version (may possibly be different from what will be stored in Dockstore")
    private String email;
    @Schema(description = "Description found from parsing the version (may possibly be different from what will be stored in Dockstore")
    private String description;
    @Schema(description = "Information from parsing the version, will be directly stored in Dockstore")
    private ParsedInformation parsedInformation;
    @Schema(description = "List of SourceFiles returned after parsing a non-hosted entry")
    private List<SourceFile> sourceFiles;

    public List<String> getSecondaryFilePaths() {
        return secondaryFilePaths;
    }

    public void setSecondaryFilePaths(List<String> secondaryFilePaths) {
        this.secondaryFilePaths = secondaryFilePaths;
    }

    public String getClonedRepositoryAbsolutePath() {
        return clonedRepositoryAbsolutePath;
    }

    public void setClonedRepositoryAbsolutePath(String clonedRepositoryAbsolutePath) {
        this.clonedRepositoryAbsolutePath = clonedRepositoryAbsolutePath;
    }

    public LanguageParsingRequest getLanguageParsingRequest() {
        return languageParsingRequest;
    }

    public void setLanguageParsingRequest(LanguageParsingRequest languageParsingRequest) {
        this.languageParsingRequest = languageParsingRequest;
    }


    public ParsedInformation getParsedInformation() {
        return parsedInformation;
    }

    public void setParsedInformation(ParsedInformation parsedInformation) {
        this.parsedInformation = parsedInformation;
    }

    public VersionTypeValidation getVersionTypeValidation() {
        return versionTypeValidation;
    }

    public void setVersionTypeValidation(VersionTypeValidation versionTypeValidation) {
        this.versionTypeValidation = versionTypeValidation;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<SourceFile> getSourceFiles() {
        return sourceFiles;
    }

    public void setSourceFiles(List<SourceFile> sourceFiles) {
        this.sourceFiles = sourceFiles;
    }
}
