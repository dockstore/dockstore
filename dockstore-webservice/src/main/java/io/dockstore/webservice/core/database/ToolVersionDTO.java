package io.dockstore.webservice.core.database;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import io.dockstore.common.DescriptorLanguage;

public class ToolVersionDTO {
    private final long id;
    private final long entryId;

    private final String author;
    private final String name;
    //    private final String url;
    private final boolean production;
    private final List<ImageDTO> images = new ArrayList<>();
    private final List<DescriptorLanguage.FileType> descriptorTypes = new ArrayList<>();
    //    private final Boolean containerFile;
    private final String metaVersion;
    private final boolean verified;
    private final List<String> verifiedSource = new ArrayList<>();
    private final boolean signed = false; // We don't support this feature yet
    private final List<String> includedApps = new ArrayList<>();
    private final boolean hidden;
    private static final Gson GSON = new Gson();

    @SuppressWarnings("checkstyle:ParameterNumber")
    public ToolVersionDTO(final long id, final long entryId, final String author, final Boolean verified, final String name, final boolean production,
            final Date date, final boolean hidden, final String verifiedSource) {
        this.entryId = entryId;
        this.name = name;
        this.production = production;
        this.id = id;
        this.author = author;
        this.verified = verified != null && verified.booleanValue();
        this.metaVersion = String.valueOf(date != null ? date : new Date(0));
        this.hidden = hidden;
        if (verifiedSource != null) {
            // TODO: Duplicating logic from Version.getVerifiedSources
            this.verifiedSource.addAll(Arrays.asList(GSON.fromJson(Strings.nullToEmpty(verifiedSource), String[].class)));
        }
    }

    public long getEntryId() {
        return entryId;
    }

    public String getAuthor() {
        return author;
    }

    public String getName() {
        return name;
    }

    public boolean isProduction() {
        return production;
    }

    public long getId() {
        return id;
    }

    public List<ImageDTO> getImages() {
        return images;
    }

    public List<DescriptorLanguage.FileType> getDescriptorTypes() {
        return descriptorTypes;
    }

    public List<String> getVerifiedSource() {
        return verifiedSource;
    }

    public List<String> getIncludedApps() {
        return includedApps;
    }

    public String getMetaVersion() {
        return metaVersion;
    }

    public boolean isSigned() {
        return signed;
    }

    public boolean isVerified() {
        return verified;
    }

    public boolean isHidden() {
        return hidden;
    }
}


