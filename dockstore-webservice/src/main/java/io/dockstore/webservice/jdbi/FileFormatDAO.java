package io.dockstore.webservice.jdbi;

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
        return uniqueResult(namedTypedQuery("io.dockstore.webservice.core.FileFormat.findByFileFormatValue").setParameter("fileformatValue", fileFormatValue));
    }

    public String create(FileFormat fileFormat) {
        String id = persist(fileFormat).getValue();
        currentSession().flush();
        return id;
    }
}
