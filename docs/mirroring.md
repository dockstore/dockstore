# Mirroring - Specification

## Overview

The goal of this feature is to have multiple GA4GH registries content available
through Dockstore.  Each register themselves in a JSON document hosted here:

    https://github.com/ga4gh/tool-registry-schemas/blob/develop/registry.json

For each of these sites listed in this file (that aren't dockstore) go ahead
and list their content.  While listing content, create entries in the Dockstore
database so that Dockstore can display content from other GA4GH-compatible
sites.  The Dockstore UI then needs to be updated to display these registrations
and, when a user clicks on them in Dockstore, they should be taken to the
remote registry.

## Functional Components

To make the above possible I think we need:

* database changes to Dockstore, basically a new table to host information about remote entries
* a command line script that will index known GA4GH sites and place information in the local table
* changes to the Dockstore-specific search endpoint and UI to support rendering of remote entries

## Issues

* https://github.com/ga4gh/dockstore/issues/232

## Schema

* http://editor.swagger.io/#/?import=https://raw.githubusercontent.com/ga4gh/tool-registry-schemas/develop/src/main/resources/swagger/ga4gh-tool-discovery.yaml
