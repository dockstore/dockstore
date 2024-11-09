package io.dockstore.webservice.helpers.doi;

import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.MetadataResourceHelper;

public final class DoiHelper {

    public String createDoi(Entry<?, ?> entry, Version<?> version) {
        String name = computeName(entry, version);
        String url = computeUrl(entry, version);
        String metadata = computeMetadata(name, entry, version);
        return new DummyDoiRegistrar().createDoi(name, url, metadata);
    }

    public String createDoi(Entry<?, ?> entry) {
        /*
        // TODO
        String id = computeId(entry);
        String metadata = computeMetadata(id, entry, defaultVersion);
        String url = computeUrl(entry);
        return new EzidDoiRegistrar().createDoi(id, metadata, url);
        */
        return "10.TODO/TODO";
    }

    public static String computeName(Entry<?, ?> entry, Version<?> version) {
        return "10.5072/FK2.%d.%d".formatted(entry.getId(), version.getId());
    }

    public static String computeMetadata(String name, Entry<?, ?> entry, Version<?> version) {
        return DataCiteHelper.createDataCiteXmlMetadataForVersion(name, entry, version);
    }

    public static String computeUrl(Entry<?, ?> entry, Version<?> version) {
        return MetadataResourceHelper.createVersionURL(entry, version);
    }
}
