package io.dockstore.webservice.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.dockstore.common.EntryType;
import io.dockstore.webservice.helpers.statelisteners.ElasticListener;
import io.swagger.api.impl.ToolsImplCommon;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A container for information about each type of entry, including the singular and plural "terms" used to
 * refer to the entry type, the path from the site root to the directory where that entry type is displayed, etc.
 * Intended to provide information about an entry type, rather than a particular instance of the entry type.
 * Useful for parameterizing code by entry type.
 */
@Schema(description = "Information about an entry type")
public class EntryTypeMetadata {

    private static final String NONE = "";

    public static final EntryTypeMetadata TOOL =
        new EntryTypeMetadata(EntryType.TOOL, "tool", "tools", "containers", true, "", true, "tools", true, ElasticListener.TOOLS_INDEX);
    public static final EntryTypeMetadata WORKFLOW =
        new EntryTypeMetadata(EntryType.WORKFLOW, "workflow", "workflows", "workflows", true, ToolsImplCommon.WORKFLOW_PREFIX + "/", true, "workflows", true, ElasticListener.WORKFLOWS_INDEX);
    public static final EntryTypeMetadata SERVICE =
        new EntryTypeMetadata(EntryType.SERVICE, "service", "services", "services", true, ToolsImplCommon.SERVICE_PREFIX + "/", false, NONE, false, NONE);
    public static final EntryTypeMetadata APPTOOL =
        new EntryTypeMetadata(EntryType.APPTOOL, "tool", "tools", "containers", true, "", true, "tools", true, ElasticListener.TOOLS_INDEX);
    public static final EntryTypeMetadata NOTEBOOK =
        new EntryTypeMetadata(EntryType.NOTEBOOK, "notebook", "notebooks", "notebooks", true, ToolsImplCommon.NOTEBOOK_PREFIX + "/", false, NONE, true, ElasticListener.NOTEBOOKS_INDEX);

    private final EntryType type;
    private final String term;
    private final String termPlural;
    private final String sitePath;
    private final boolean trsSupported;
    private final String trsPrefix;
    private final boolean searchSupported;
    private final String searchEntryType;
    private final boolean esSupported;
    private final String esIndex;

    @SuppressWarnings("checkstyle:ParameterNumber")
    protected EntryTypeMetadata(EntryType type, String term, String termPlural, String sitePath, boolean trsSupported, String trsPrefix, boolean searchSupported, String searchEntryType, boolean esSupported, String esIndex) {
        this.type = type;
        this.term = term;
        this.termPlural = termPlural;
        this.sitePath = sitePath;
        this.trsSupported = trsSupported;
        this.trsPrefix = trsPrefix;
        this.searchSupported = searchSupported;
        this.searchEntryType = searchEntryType;
        this.esSupported = esSupported;
        this.esIndex = esIndex;
    }

    /**
     * Get the entry type.
     */
    @Schema(description = "Entry type")
    public EntryType getType() {
        return type;
    }

    /**
     * Get the singular term used to refer to an instance of this entry type.
     * For example: "workflow"
     */
    @Schema(description = "Singular term used to refer to an instance of this entry type")
    public String getTerm() {
        return term;
    }

    /**
     * Get the plural term used to refer to instances of this entry type.
     * For example: "workflows"
     */
    @Schema(description = "Plural term used to refer to instances of this entry type")
    public String getTermPlural() {
        return termPlural;
    }

    /**
     * Get the relative path from the root of the site to the 'pages' where entries of this type are displayed.
     * If this method returns X, the URL to a given entry of this type can be formed as "[baseUrl]/X/[entryPath]"
     */
    @Schema(description = "Relative path from the root of the site to the 'pages' where entries of this type are displayed")
    public String getSitePath() {
        return sitePath;
    }

    /**
     * Does TRS support this entry type?
     */
    @Schema(description = "TRS support for this entry type")
    public boolean isTrsSupported() {
        return trsSupported;
    }

    /**
     * Get the prefix of the TRS ID for this entry type, including any trailing slash.
     * For example: "#workflows/"
     */
    @Schema(description = "TRS ID prefix for this entry type")
    public String getTrsPrefix() {
        return trsPrefix;
    }

    /**
     * Is search supported in the UI for this entry type?
     */
    @Schema(description = "Search support for this entry type")
    public boolean isSearchSupported() {
        return searchSupported;
    }

    /**
     * Get the search 'entryType' parameter value for this entry type.
     */
    @Schema(description = "Search 'entryType' parameter value for this entry type")
    public String getSearchEntryType() {
        return searchEntryType;
    }

    /**
     * Is ES indexing supported for this entry type?
     */
    @JsonIgnore
    public boolean isEsSupported() {
        return esSupported;
    }

    /**
     * Get the ElasticSearch index for the entry type.
     */
    @JsonIgnore
    public String getEsIndex() {
        return esIndex;
    }

    /**
     * Get a list of the metadata for each entry type.
     */
    public static List<EntryTypeMetadata> values() {
        return List.of(TOOL, WORKFLOW, SERVICE, APPTOOL, NOTEBOOK);
    }
}
