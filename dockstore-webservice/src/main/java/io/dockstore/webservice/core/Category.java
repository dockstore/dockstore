package io.dockstore.webservice.core;

// import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
// import java.io.Serializable;
// import java.sql.Timestamp;
// import java.util.List;
// import java.util.stream.Collectors;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Table;

// import javax.persistence.Transient;

/**
 * Describes a Category, which is a curated group of entries.  In this
 * implementation, a Category is a Collection under the hood.
 */

@ApiModel("Category")
@Schema(name = "Category", description = "Category of entries")
@Entity
@Table(name = "collection")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)

public class Category extends Collection {
}
