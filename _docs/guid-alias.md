---
title: GUID Alias
permalink: /docs/publisher-tutorials/guid-alias/
---
# GUID Alias

If you would like to setup aliases for your tool or workflow, you can now do so through our API for now. Aliases deal with the situation where there are multiple Dockstore-like sites (including Dockstore), and you have the same tool on each platform. You can use an alias to give this tool a unique name that could potentially be valid across all sites, independent of site specific naming conventions.

Use the `/entries/{id}/aliases` endpoint to add new aliases. You can specify the aliases as a comma-delimited list.

**Note:** Aliases are restricted to alphanumerical strings that are case-insensitive and can contain internal hyphens.
