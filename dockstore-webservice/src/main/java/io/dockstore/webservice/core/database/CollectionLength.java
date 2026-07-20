package io.dockstore.webservice.core.database;

/**
 * JPA convenience for returning the lengths of collections of workflows, tools, etc.
 * @param id identifier for the collection
 * @param length length of the collection
 */
public record CollectionLength(Long id, Long length) {

}
