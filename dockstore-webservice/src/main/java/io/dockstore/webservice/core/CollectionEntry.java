package io.dockstore.webservice.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dockstore.common.SourceControl;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.InvalidObjectException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Used to retrieve specific entry fields from workflows/tools.  Also used in response for all endpoints that return a single collection.
 */
public class CollectionEntry implements Serializable {
    private String entryPath;
    @ApiModelProperty(dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Date dbUpdateDate;
    private long id;
    private String entryType;
    private String versionName;
    private List<String> descriptorTypes = new ArrayList<String>();
    private boolean verified = false;
    private String topic;
    private Entry.TopicSelection topicSelection;
    private boolean isApprovedAITopic;
    private List<String> labels = new ArrayList<String>();
    @JsonProperty("categories")
    private List<CategorySummary> categorySummaries = new ArrayList<>();

    public CollectionEntry() throws InvalidObjectException {
        throw new InvalidObjectException("Invalid CollectionEntry");
    }

    /**
     * @param id
     * @param dbUpdateDate
     * @param entryTypeString
     * @param sourceControl
     * @param organization
     * @param repository
     * @param entryName
     * @deprecated assumes verification is false, but one version may in fact be verified
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    @Deprecated
    public CollectionEntry(long id, Date dbUpdateDate, String entryTypeString, SourceControl sourceControl, String organization, String repository, String entryName)  {
        this(id, dbUpdateDate, entryTypeString, sourceControl, organization, repository, entryName, null, false);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public CollectionEntry(long id, Date dbUpdateDate, String entryTypeString, SourceControl sourceControl, String organization, String repository, String entryName, String versionName, boolean verified)  {
        setEntryType(entryTypeString);
        setDbUpdateDate(dbUpdateDate);
        setId(id);
        setEntryPathFromFragments(sourceControl.toString(), organization, repository, entryName);
        setVersionName(versionName);
        setVerified(verified);
    }


    /**
     * @param id
     * @param dbUpdateDate
     * @param entryTypeString
     * @param registry
     * @param organization
     * @param repository
     * @param entryName
     * @deprecated assumes verification is false, but one version may in fact be verified
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    @Deprecated
    public CollectionEntry(long id, Date dbUpdateDate, String entryTypeString, String registry, String organization, String repository, String entryName)  {
        this(id, dbUpdateDate, entryTypeString, registry, organization, repository, entryName, null, false);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public CollectionEntry(long id, Date dbUpdateDate, String entryTypeString, String registry, String organization, String repository, String entryName, String versionName, boolean verified)  {
        setEntryType(entryTypeString);
        setDbUpdateDate(dbUpdateDate);
        setId(id);
        setEntryPathFromFragments(registry, organization, repository, entryName);
        setVersionName(versionName);
        setVerified(verified);
    }

    private void setEntryPathFromFragments(String sourceControl, String organization, String repository, String entryName) {
        setEntryPath(sourceControl + '/' + organization + '/' + repository + (entryName == null || "".equals(entryName) ? "" : '/' + entryName));
    }

    public void setEntryPath(String entryPath) {
        this.entryPath = entryPath;
    }

    public String getEntryPath() {
        return entryPath;
    }

    public Date getDbUpdateDate() {
        return dbUpdateDate;
    }

    public void setDbUpdateDate(Date dbUpdateDate) {
        this.dbUpdateDate = dbUpdateDate;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getEntryType() {
        return entryType;
    }

    public void setEntryType(String entryType) {
        this.entryType = entryType;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public boolean getVerified() {
        return verified;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public List<String> getDescriptorTypes() {
        return descriptorTypes;
    }

    public void setDescriptorTypes(List<String> descriptorTypes) {
        this.descriptorTypes = descriptorTypes;
    }

    public List<CategorySummary> getCategorySummaries() {
        return categorySummaries;
    }

    public void setCategorySummaries(List<CategorySummary> categorySummaries) {
        this.categorySummaries = categorySummaries;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Entry.TopicSelection getTopicSelection() {
        return topicSelection;
    }

    public void setTopicSelection(Entry.TopicSelection topicSelection) {
        this.topicSelection = topicSelection;
    }

    public boolean getIsApprovedAITopic() {
        return isApprovedAITopic;
    }

    public void setIsApprovedAITopic(boolean approvedAITopic) {
        this.isApprovedAITopic = approvedAITopic;
    }
}
