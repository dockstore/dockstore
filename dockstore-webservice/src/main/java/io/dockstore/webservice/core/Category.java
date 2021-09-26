package io.dockstore.webservice.core;

import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;

/**
 * Describes a Category, which is a dockstore-curated group of entries.
 *
 * A Category is a Collection under the hood.
 *
 * In the pure definition of a Category, a Category would have no owner.
 * However, since one goal of this "Categories-piggybacked-on-Collections"
 * implementation was to utilize the existing Collection creation/update
 * infrastructure and UI, each Category is owned by an Organization,
 * typically the categorizer Organization that created the Category.
 *
 * As with Collections, each Category contains a set of Entry+version
 * pairs, where the version can be undefined.  An Entry is defined as
 * being "in" the Category if the Category contains the Entry,
 * irregardless of the version.
 */

@ApiModel("Category")
@Schema(name = "Category", description = "Category of entries")
@Entity
@NamedQueries({
        @NamedQuery(name = "io.dockstore.webservice.core.Category.getCategories", query = "SELECT c FROM Category c"),
        @NamedQuery(name = "io.dockstore.webservice.core.Category.findByName", query = "SELECT c FROM Category c where lower(c.name) = lower(:name)")
})

public class Category extends Collection {
}
