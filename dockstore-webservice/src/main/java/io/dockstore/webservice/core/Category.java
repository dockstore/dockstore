package io.dockstore.webservice.core;

import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;

/**
 * Describes a Category, which is a curated group of entries.  In this
 * implementation, a Category is a Collection under the hood.
 */

@ApiModel("Category")
@Schema(name = "Category", description = "Category of entries")
@Entity
@NamedQueries({
        @NamedQuery(name = "io.dockstore.webservice.core.Category.getCategories", query = "SELECT c FROM Category c"),
        @NamedQuery(name = "io.dockstore.webservice.core.Category.findByName", query = "SELECT c FROM Category c where lower(c.name) = lower(:name)")
})

public class Category extends Collection {

    public Category() {
    }

    public Category(Collection c) {
        setName(c.getName());
        setDescription(c.getDescription());
        setDisplayName(c.getDisplayName());
        setTopic(c.getTopic());
        setOrganization(c.getOrganization());
    }
}
