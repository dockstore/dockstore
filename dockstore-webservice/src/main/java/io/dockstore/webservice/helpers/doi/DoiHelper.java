package io.dockstore.webservice.helpers.doi;

import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Version;

public final class DoiHelper {

    public String createDoi(Entry<?, ?> entry, Version<?> version) {
        String id = computeId(entry, version);
        String metadata = computeMetadata(id, entry, version);
        String url = computeUrl(entry, version);
        return new DummyDoiRegistrar().createDoi(id, metadata, url);
    }

    public String createDoi(Entry<?, ?> entry) {
        /*
        String id = computeId(entry);
        String metadata = computeMetadata(id, entry, defaultVersion);
        String url = computeUrl(entry);
        return new EzidDoiRegistrar().createDoi(id, metadata, url);
        */
        return null;
    }

    public static String computeId(Entry<?, ?> entry, Version<?> version) {
        return "10.5072/FK2.%d.%d".formatted(entry.getId(), version.getId());
    }

    public static String computeMetadata(String id, Entry<?, ?> entry, Version<?> version) {
        return DataCiteHelper.createDataCiteXmlMetadataForVersion(id, entry, version);
    }

    public static String computeUrl(Entry<?, ?> entry, Version<?> version) {
        return "https://www.test.com/";
    }
}
