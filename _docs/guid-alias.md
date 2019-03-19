---
title: GUID Alias
permalink: /docs/publisher-tutorials/guid-alias/
---
# GUID Alias
## Tools and Workflows
If you would like to setup aliases for your tool or workflow, you can now do so through our API for now. Aliases deal with the situation where there are multiple Dockstore-like sites (including Dockstore), and you have the same tool on each platform. You can use an alias to give this tool a unique name that could potentially be valid across all sites, independent of site specific naming conventions.

Use the `/entries/{id}/aliases` endpoint to add new aliases. You can specify the aliases as a comma-delimited list.

**Note:** Aliases are restricted to alphanumerical strings that are case-insensitive and can contain internal hyphens.

## Organizations and Collections
> For Dockstore 1.6.0+

An alias is a unique identifier for a organization or collection, scoped within the entity type. In other words, the alias of an organization is unique across organizations, but not within collections. Similarly, the alias of a collection is unique across collections, but not within organizations.

Currently the only way to add an alias to an organization and collection is through our API. You can use our [Swagger UI](https://dockstore.org/api/static/swagger-ui/index.html#) to add an alias to approved organizations and collections. **An entity can have more than one alias.**

The main benefit of an alias is as a permanent identifier for an entity that cannot change or be removed. You can link to an organization alias called `MyAlias` using the link [https://dockstore.org/aliases/organizations/MyAlias](https://dockstore.org/aliases/organizations/MyAlias). This is the recommended way to link to organizations and collections, as names can be changed, making linking by names quite fragile.