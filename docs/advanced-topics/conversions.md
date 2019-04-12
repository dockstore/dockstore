## Write-API Conversion Process

This is a service aimed at two tasks

1. Providing a concrete reference implementation of a proposed GA4GH Write API.
2. Providing a utility for developers to convert plain CWL/WDL files and Dockerfiles into GitHub repos storing those plain CWL/WDL files and Quay.io repos storing Docker images built from those Dockerfiles. This can be used by those converting tools described in other formats into "Dockstore-friendly" tools that can be quickly registered and published in Dockstore by using the Write API Client's publish command or programmatically via the Dockstore API. It is an alternative to using a GUI to register tools on Dockstore.

This is intended to be used by:

* Tool Migrators - Developers that have access to a large number of tools in some different format and wants to migrate them all programmatically to Dockstore with minimal effort.

* Tool Developers - Developers of tools that wants a quick and simple way of creating one without spending a large amount of time to post a single Dockerfile and CWL descriptor to implement each tool.

### Building/Downloading the API jars

You have the option to either build or download the API jars.

#### Build

```
git clone https://github.com/dockstore/write_api_service.git
cd write_api_service
mvn clean install -DskipTests
```
The built jars will be available as:

`write-api-service/target/write-api-service*.jar`

`write-api-client/target/write-api-client*shaded.jar`

#### Download

Additionally, you can download the Write API jars using the following:

```
wget https://artifacts.oicr.on.ca/artifactory/collab-release/io/dockstore/write-api-client/1.0.2/write-api-client-1.0.2-shaded.jar
wget https://artifacts.oicr.on.ca/artifactory/collab-release/io/dockstore/write-api-service/1.0.2/write-api-service-1.0.2.jar
```

Note that the client one is a shaded jar.

### Web Service Prerequisites

* GitHub token - Learn how to create tokens on GitHub here. You will need the scope "repo".

* GitHub organization(s) - Your GitHub token must have access to at least one existing GitHub organization. The organization can be changed as long as the the GitHub token still has access to it. The Write API web service currently does not create GitHub organizations. The name of this organization must match the Quay.io organization. This organization will contain the repository that will be created.

* Quay.io organization and Quay.io token

    You will need an existing Quay.io organization. The Write API currently does not create Quay.io organizations. The name of this organization must match the GitHub organization. Once you have an organization, a token must be created for it.

    This organization will contain the repository that will be created. Changing the Quay.io organization requires the token to be recreated/changed too.

    Learn how to create a token on Quay.io for your organizations here under the heading "Generating a Token (for internal application use)". You will need to provide these permissions:
    
    * Create Repositories
    
    * View all visible repositories
    
    * Read/Write to any accessible repository
    
#### Server Configuration

The web service alone only requires a GitHub and Quay.io token. There are two ways to specify your tokens.

- Environmental variables.
    You can set them in Bash like the following:
```
export quayioToken=<your token here>
export githubToken=<your token here>
```

- The YAML configuration file. The tokens can be entered in the top two lines. An example of a YAML configuration file can be seen [here](https://github.com/dockstore/write_api_service/blob/develop/write-api-service/src/main/resources/example.yml).

Run the service using a configuration file:
`java -jar write-api-service-*.jar server example.yml`
The example.yml shown previously uses port 8082 by default, this can be changed. Note this port number, it will later be used for the Write API Client properties file.

After running the webservice, you can check out the web service endpoints through swagger. By default, it is available at http://localhost:8082/static/swagger-ui/index.html.

The basic workflow is that GitHub repos are created when posting a new tool. When files are posted or put to a version of a tool, we will create or delete and re-create a GitHub release/branch/tag with a matching name. When Dockerfiles are added, the tool will be created and built as a Quay.io repo. After adding both Dockerfiles and descriptors, you basically have a tool that is ready to be quickly registered and published under the Dockstore web service. Go to Dockstore, do a refresh, and then hit quick register on the repos that you wish to publish. You can also do this programmatically through the write api client

#### Client Prerequisites

* Write API web service and all its prerequisites

By now, then web service should be up and running with valid GitHub and Quay.io tokens. If not, please return to the web service usage section to get that running first. It is advised to ensure that the Write API web service is functioning correctly before using the client.

* [Dockstore token](https://docs.dockstore.org//register-on-dockstore/)

Follow the "Getting Started with Dockstore" tutorial to get a Dockstore token. Note this down, it will later be used in the Write API client properties file.

* Dockstore server-url

The Dockstore tutorial earlier would've specified the server-url alongside the token. Unless you're running your own dockstore webservice, the Dockstore production server-url is "https://www.dockstore.org/api". Note this down as it will be used later in the Write API client properties file.

* Quay.io integration

In order to publish to Dockstore, Quay.io must be linked to Dockstore. See [Dockstore](https://docs.dockstore.org//register-on-dockstore/#linking-with-external-services) on how to link your Quay.io account to Dockstore.

* Write API web service URL

You will need to know the URL of the Write API web service you ran previously. If you've been using the example.yml, it should be "http://localhost:8082/api/ga4gh/v1"

#### Client Configuration

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


### Client Usage
Here is the general usage information for the client:

```
$ java -jar write-api-client-*-shaded.jar --help
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

