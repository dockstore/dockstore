package io.dockstore.webservice.core;

/**
 * JPA convenience for returning the lengths of collections of workflows, tools, etc.
 * @param id
 * @param length
 */
public record CollectionLength(Long id, Long length) {

}
