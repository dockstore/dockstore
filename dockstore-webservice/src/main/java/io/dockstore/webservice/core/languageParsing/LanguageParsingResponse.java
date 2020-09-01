package io.dockstore.webservice.core.languageParsing;

import java.util.List;

import io.dockstore.webservice.core.ParsedInformation;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response from the external lambda parsing service")
public class LanguageParsingResponse {
    private String clonedRepositoryAbsolutePath;
    private boolean isValid;
    private List<String> secondaryFilePaths;
    private LanguageParsingRequest languageParsingRequest;

    private ParsedInformation parsedInformation;

    public List<String> getSecondaryFilePaths() {
        return secondaryFilePaths;
    }

    public void setSecondaryFilePaths(List<String> secondaryFilePaths) {
        this.secondaryFilePaths = secondaryFilePaths;
    }

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean valid) {
        isValid = valid;
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
}
