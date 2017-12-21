/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.webservice.jdbi;

import java.lang.reflect.ParameterizedType;
import java.util.List;

import io.dockstore.webservice.core.Entry;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * @author dyuen
 */
public class EntryDAO<T extends Entry> extends AbstractDAO<T> {

    private Class<T> typeOfT;

    public EntryDAO(SessionFactory factory) {
        super(factory);
        /**
         * ewwww, don't try this at home from https://stackoverflow.com/questions/4837190/java-generics-get-class
         */
        this.typeOfT = (Class<T>)((ParameterizedType)getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

    public T findById(Long id) {
        return get(id);
    }

    public long create(T entry) {
        return persist(entry).getId();
    }

    public void delete(T entry) {
        Session session = currentSession();
        session.delete(entry);
        session.flush();
    }

    public void evict(T entry) {
        Session session = currentSession();
        session.evict(entry);
    }

    public T findPublishedById(long id) {
        return (T)uniqueResult(
                namedQuery("io.dockstore.webservice.core." + typeOfT.getSimpleName() + ".findPublishedById").setParameter("id", id));
    }

    public List<T> findAll() {
        return list(namedQuery("io.dockstore.webservice.core." + typeOfT.getSimpleName() + ".findAll"));
    }

    public List<T> findAllPublished() {
        return list(namedQuery("io.dockstore.webservice.core." + typeOfT.getSimpleName() + ".findAllPublished"));
    }

    public List<T> searchPattern(String pattern) {
        pattern = '%' + pattern + '%';
        return list(
                namedQuery("io.dockstore.webservice.core." + typeOfT.getSimpleName() + ".searchPattern").setParameter("pattern", pattern));
    }
}
