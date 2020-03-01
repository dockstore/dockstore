package io.dockstore.webservice.core;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.dockstore.common.EntryUpdateTime;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.swagger.annotations.ApiModel;

@ApiModel(value = "UserEntriesLite", description = "contains user entries")
public class UserEntriesLite {
    private List<Map<String, Object>> entryMaps;
    //private List<Entry> entries;
    private List<EntryUpdateTime> entryUpdateTimes;
    private int count;

    public UserEntriesLite(long userId, ToolDAO toolDAO, WorkflowDAO workflowDAO, int count) {
        this.count = count;
        this.entryMaps = new ArrayList<>();
        EntryDAO entryDAO;
        entryDAO = toolDAO;
        this.entryMaps.addAll(entryDAO.findEntryVersions(userId));
        entryDAO = workflowDAO;
        this.entryMaps.addAll(entryDAO.findEntryVersions(userId));
        for (Map<String, Object> entryMap: this.entryMaps) {
            Timestamp entryTimestamp = (Timestamp) entryMap.get("edbUpdateDate");
            Timestamp versionTimestamp = (Timestamp) entryMap.get("vdbUpdateDate");
            if (versionTimestamp == null || entryTimestamp.after(versionTimestamp)) {
                entryMap.put("lastUpdate", entryTimestamp);
            } else {
                entryMap.put("lastUpdate", versionTimestamp);
            }
        }
        Collections.sort(this.entryMaps, (e1, e2) -> ((Timestamp)e2.get("lastUpdate")).compareTo((Timestamp)e1.get("lastUpdate")));
        this.entryMaps = this.entryMaps.subList(0, this.count);
        //this.entries = new ArrayList<>();
        this.entryUpdateTimes = new ArrayList<>();
        for (Map<String, Object> entryMap: entryMaps) {
            Long id = (Long) entryMap.get("id");
            String type = (String) entryMap.get("entry_type");
            Timestamp timestamp = (Timestamp) entryMap.get("lastUpdate");

            if (("workflow").equals(type)) {
                entryDAO = workflowDAO;
            } else if (("tool").equals(type)) {
                entryDAO = toolDAO;
            }

            Entry entry = entryDAO.getGenericEntryById(id);
            List<String> pathElements = Arrays.asList(entry.getEntryPath().split("/"));
            String prettyPath = String.join("/", pathElements.subList(2, pathElements.size()));
            entryUpdateTimes.add(new EntryUpdateTime(entry.getEntryPath(), prettyPath, entry.getEntryType(), timestamp));

            //this.entries.add(entry);

            //            System.out.println("----------------");
            //            System.out.println(entry.get("id"));
            //            System.out.println(entry.get("entry_type"));
            //            System.out.println(entry.get("vdbUpdateDate"));
        }
    }
    //    public entrybyID(){
    //        entries.forEach(entry ->{
    //
    //        });
    //    }

    public List<Map<String, Object>> getEntryMaps() {
        return entryMaps;
    }

    public List<EntryUpdateTime> getEntryUpdateTimes() {
        return entryUpdateTimes;
    }
}
