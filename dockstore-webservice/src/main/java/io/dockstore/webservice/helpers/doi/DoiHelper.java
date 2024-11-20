package io.dockstore.webservice.helpers.doi;

import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.MetadataResourceHelper;

public final class DoiHelper {

    public String createDoi(Entry<?, ?> entry, Version<?> version) {
        String name = computeName(entry, version);
        String url = computeUrl(entry, version);
        String metadata = computeMetadata(name, entry, version);
        return getDoiService().createDoi(name, url, metadata);
    }

    public String createDoi(Entry<?, ?> entry) {
        /*
        // TODO
        Version = getRepresentativeVersion(entry);
        String id = computeId(entry);
        String metadata = computeMetadata(id, entry, defaultVersion);
        String url = computeUrl(entry);
        return getDoiService().createDoi(id, metadata, url);
        */
        return "10.TODO/TODO";
    }

    private String computeName(Entry<?, ?> entry, Version<?> version) {
        return "10.5072/FK2.%d.%d".formatted(entry.getId(), version.getId());
    }

    private String computeMetadata(String name, Entry<?, ?> entry, Version<?> version) {
        return DataCiteHelper.createDataCiteXmlMetadataForVersion(name, entry, version);
    }

    private String computeUrl(Entry<?, ?> entry, Version<?> version) {
        return MetadataResourceHelper.createVersionURL(entry, version);
    }

    private DoiService getDoiService() {
        return new DummyDoiService();
    }
}
