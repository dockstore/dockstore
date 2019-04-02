---
title: Conversion Processes
permalink: /docs/publisher-tutorials/conversions/
---

## Write-API Conversion Process
### Client Configuration
The configuration file used by the write-api-client is located at ~/.dockstore/write.api.config.properties
It should look something like this:

```
dockstoreToken=abcdefghijklmnopqrstuvwxyz1234567890
server-url=https://www.dockstore.org/api
organization=test_organization
repo=test_repository
write-api-url=http://localhost:8080/api/ga4gh/v1
```

"dockstoreToken" is acquired from your account page on the dockstore website .
"server-url" is the dockstore server url.
"organization" is the organization/user of the repository to create.
"repo" is the repository to create.
"write-api-url" is the url of the write-api-service

### Server Configuration
The write-api-service uses githubToken and quayToken from the server yml file and looks something like this:

```
quayioToken: abcdefghijklmnopqrstuvwxyz1234567890
githubToken: abcdefghijklmnopqrstuvwxyz1234567890
...
```
The github token can be attained by following the instructions from [GitHub Help](https://help.github.com/articles/creating-a-personal-access-token-for-the-command-line/)
The quay.io token can be attained by following the instruction from [Quay.io API docs](https://docs.quay.io/api/) -> OAuth 2 Access Tokens -> Generating a Token (for internal application use).  When generating the token, provide these 3 permissions:
* Create Repositories
* View all visible repositories
* Read/Write to any accessible repositories

### Client Usage
```
Usage: client [options] [command] [command options]
  Options:
    --help
      Prints help for the client.
      Default: false
  Commands:
    add      Add the Dockerfile and CWL file(s) using the write API.
      Usage: add [options]
        Options:
        * --Dockerfile
            The Dockerfile to upload
        * --cwl-file
            The cwl descriptor to upload
          --cwl-secondary-file
            The optional secondary cwl descriptor to upload
          --help
            Prints help for the add command
            Default: false
          --version
            The version of the tool to upload to

    publish      Publish tool to dockstore using the output of the 'add'
            command.
      Usage: publish [options]
        Options:
          --help
            Prints help for the publish command.
            Default: false
        * --tool
            The json output from the 'add' command.
```

### Sample Client Output
```
client add --Dockerfile Dockerfile --cwl-file Dockstore.cwl --cwl-secondary-file Dockstore2.cwl --version 3.0
{
  "githubURL": "https://github.com/dockstore-testing/test_repo3",
  "quayioURL": "https://quay.io/repository/dockstore-testing/test_repo3",
  "version": "3.0"
}
```
You can pipe the output like this:
```
client add --Dockerfile Dockerfile --cwl-file Dockstore.cwl --cwl-secondary-file Dockstore2.cwl --version 3.0 > test.json
```
and then:
```
client publish --tool test.json
```


## Converting File-path Based Imports to Public http(s) Based Imports

See [https://cromwell.readthedocs.io/en/develop/Imports/](https://cromwell.readthedocs.io/en/develop/Imports/) for general knowledge on imports.  

Imports allow you to reference other files in your workflow.  There are two types of resources that are supported in imports: http(s) and file-path based. Any public http(s) based URL can be used as the resource for an import, such as a website, GitHub, GA4GH compliant TES endpoint, etc.

There are times when you may want to convert file-path based imports to public http(s) imports.  One such reason is to ensure compatibility with FireCloud since it currently does not support file-path based imports.  There are many different ways to convert to a public http(s) based import, the following are two examples.

You can host your file on GitHub and import it in the workflow descriptor like this:

```
import "https://raw.githubusercontent.com/DataBiosphere/topmed-workflows/1.11.0/variant-caller/variant-caller-wdl/topmed_freeze3_calling.wdl" as TopMed_variantcaller
import "https://raw.githubusercontent.com/DataBiosphere/topmed-workflows/1.11.0/variant-caller/variant-caller-wdl-checker/topmed-variantcaller-checker.wdl" as checker
...
```

Similarly, you can also host your file on a public google bucket and import it in the workflow descriptor like this:

```
import "http://storage.googleapis.com/..."
...
```
