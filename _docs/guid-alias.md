---
title: GUID Alias
permalink: /docs/publisher-tutorials/guid-alias/
---
# GUID Alias
> For Dockstore 1.5.0+

If you would like to setup multiple aliases for your tool or workflow, you can now do so, but only through our API for now. Aliases deal with the situation where there are multiple Dockstore-like sites (including Dockstore), and you have the same tool on each platform. An alias will give this tool a unique name that is valid across all sites, independent of site specific naming conventions.

Use the `/entries/{id}/aliases` endpoint to add new aliases. You can specify the aliases as a comma-delimited list.

**Note:** Aliases are restricted to alphanumerical strings that are case-insensitive and can contain internal hyphens.