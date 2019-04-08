---
layout: post
title:  "Upcoming Features"
date:   2017-05-05 09:00:00 -0400
categories: dockstore update
---
To give you a taste of what we're working on for the next major version of Dockstore, we're looking at features in the following main areas:

* Searching!
  * As Dockstore grows, we've noticed that our current solution for searching tools (go to [Tools](https://dockstore.org/tools) or [Workflows](https://dockstore.org/workflows) and type in the search box) is becoming less useful. Look for more useful ways to search and filter tools and workflows in the next version
* More ways to launch tools and workflows
  * We're working with partners to promote new ways to run CWL and WDL tools and workflows
* UI rewrite
  * We're currently migrating our UI from AngularJS to Angular (2), watch for performance improvements and usability improvements in this area
* Write API Web Service and Client!
  * With just a CWL descriptor and Dockerfile, this allows you to programmatically create GitHub and Quay.io repositories and then register and publish the tool on Dockstore in just 2 commands.  Publishing tools on Dockstore has gotten a lot easier.  See [GitHub](https://github.com/dockstore/write_api_service/) for more info on how to use the Write API.  See [this doc](/docs/publisher-tutorials/ways-to-register-tools-on-dockstore/) for information on different ways to register tools on Dockstore and when to use this Write API.

As usual, we're open to suggestions. If you have one or if you spot a bug, drop us a line on [GitHub](https://github.com/ga4gh/dockstore/issues)

## April 19, 2017 - Dockstore 1.2 Release

The latest Dockstore major release includes a large number of new features and fixes.
A subset of highlighted new features follows.

### Highlighted New Features

* Support for private tools
  * users can register tools where users will need to ask the original author for access
* Support for [private](https://dockstore.org/docs/docker_registries) Docker images hosted in GitLab and Amazon ECR
* Allow users to star tools and workflows
* Stargazers page to show all users who have starred a particular tool or workflow
* Support for [file provisioning plugins](https://github.com/ga4gh/dockstore/tree/develop/dockstore-file-plugin-parent)
* Better error messaging passed along from a newer cwltool version
* Compatibility with a Write API service for programmatically adding tools

### Breaking Changes

* The default Dockstore install no longer includes S3 support. Instead, S3 support is provided by a plugin that can be installed via `dockstore plugin download`
* The command `dockstore tool launch` used to use `--local-entry` as a flag to indicate that `--entry` was pointing at a local file. Now, it replaces `--entry`. i.e. use `dockstore tool launch --local-entry <your local file>` rather than `dockstore tool launch --local-entry --entry <your local file>`
* Update your cwltool install, details in the onboarding wizard

