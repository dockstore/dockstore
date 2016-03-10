package io.dockstore.webservice;

import com.google.common.collect.Lists;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.jdbi.EntryDAO;
import org.apache.http.HttpStatus;

import java.util.List;
import java.util.Set;

/**
 * This class contains code for interacting with versions for all types of entries.
 *
 * Created by dyuen on 10/03/16.
 */
public class EntryVersionHelper<T extends Entry> {

        private final EntryDAO dao;

        public EntryVersionHelper(EntryDAO dao){
                this.dao = dao;
        }

        public T filterContainersForHiddenTags(T entry){
                return filterContainersForHiddenTags(Lists.newArrayList(entry)).get(0);
        }

        public List<T> filterContainersForHiddenTags(List<T> entries) {
                for(T entry : entries){
                        dao.evict(entry);
                        // need to have this evict so that hibernate does not actually delete the tags
                        Set<Version> versions = entry.getVersions();
                        versions.removeIf(v -> v.isHidden());
                }
                return entries;
        }

        public SourceFile getSourceFile(Long workflowId, String tag, SourceFile.FileType fileType) {
                T entry = (T)dao.findById(workflowId);
                Helper.checkEntry(entry);
                this.filterContainersForHiddenTags(entry);
                Version tagInstance = null;

                if (tag == null) {
                        tag = "latest";
                }

                // todo: why the cast here?
                for (Object o : entry.getVersions()) {
                        Version t = (Version)o;
                        if (t.getName().equals(tag)) {
                                tagInstance = t;
                        }
                }

                if (tagInstance == null) {
                        throw new CustomWebApplicationException("Invalid version.", HttpStatus.SC_BAD_REQUEST);
                } else {
                        for (Object o : tagInstance.getSourceFiles()) {
                                SourceFile file = (SourceFile)o;
                                if (file.getType() == fileType) {
                                        return file;
                                }
                        }
                }
                throw new CustomWebApplicationException("File not found.", HttpStatus.SC_NOT_FOUND);
        }
}
