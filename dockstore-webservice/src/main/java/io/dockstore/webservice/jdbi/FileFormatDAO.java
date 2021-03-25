package io.dockstore.webservice.jdbi;

import java.util.List;

import io.dockstore.webservice.core.FileFormat;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;

/**
 * @author gluu
 * @since 1.5.0
 */
public class FileFormatDAO extends AbstractDAO<FileFormat> {

    public FileFormatDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public FileFormat findById(Long id) {
        return get(id);
    }

    public FileFormat findFileFormatByValue(String fileFormatValue) {
        return uniqueResult(this.currentSession().getNamedQuery("io.dockstore.webservice.core.FileFormat.findByFileFormatValue").setParameter("fileformatValue", fileFormatValue));
    }

    public String create(FileFormat fileFormat) {
        String id = persist(fileFormat).getValue();
        currentSession().flush();
        return id;
    }

    public List<FileFormat> findInputFileFormatsByEntry(Long entryId) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.FileFormat.findInputFileFormatsInEntry").setParameter("entryId", entryId));
    }

    public List<FileFormat> findOutputFileFormatsByEntry(Long entryId) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.FileFormat.findOutputFileFormatsInEntry").setParameter("entryId", entryId));
    }
}
