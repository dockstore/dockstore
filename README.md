[![Build Status](https://travis-ci.org/ga4gh/dockstore.svg?branch=develop)](https://travis-ci.org/ga4gh/dockstore) [![Coverage Status](https://coveralls.io/repos/github/ga4gh/dockstore/badge.svg?branch=develop)](https://coveralls.io/github/ga4gh/dockstore?branch=develop)
[![license](https://img.shields.io/hexpm/l/plug.svg?maxAge=2592000)](LICENSE)
[![Website](https://img.shields.io/website/https/dockstore.org.svg)](https://dockstore.org)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/ga4gh/dockstore?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Coverity Scan](https://img.shields.io/coverity/scan/9682.svg?maxAge=2592000)](https://scan.coverity.com/projects/ga4gh-dockstore)

# Dockstore

The Dockstore concept is simple, provide a place where users can share tools encapsulated in Docker and described with the Common Workflow Language (CWL) which is being recommended by the GA4GH Containers and Workflow group. This enables scientists, for example, to share analytical tools in a way that makes them machine readable and runnable in a variety of environments (SevenBridges, Toil, etc). While the Dockstore is focused on serving researchers in the biosciences the combination of Docker + CWL can be used by anyone to describe the tools and services in their Docker images in a standardized, machine-readable way.  We hope to use this project as motivation to create a GA4GH API standard for container registries and intend on making Dockstore fully compliant.

For the live site see https://dockstore.org

This repo is the web service for the Dockstore. The usage of this is to enumerate the docker containers (from quay.io and hopefully docker hub) and the workflows (from github/bitbucket) that are available to users of Dockstore.org.

For the related web UI see the [dockstore-ui](https://github.com/ga4gh/dockstore-ui) project.

## For Dockstore Users

The following section is useful for users of Dockstore (e.g. those that want to browse, register, and launch tools). 

After registering at https://dockstore.org , you will be able to download the Dockstore CLI at https://dockstore.org/onboarding

The CLI has the following dependencies

* Java (1.8.0\_66 or similar)
* cwltool (to run CWL workflows locally)

To install CWL tool:

    pip install --user cwl-runner cwltool==1.0.20160712154127 schema-salad==1.14.20160708181155 avro==1.8.1

You may need other pip installable tools like `typing` or `setuptools`.  This depends on your python environment.

## For Dockstore Developers

The following section is useful for Dockstore developers (e.g. those that want to improve or fix the Dockstore web service and UI)

### Dependencies

The dependency environment for Dockstore is described by our [Travis-CI config](https://github.com/ga4gh/dockstore/blob/develop/.travis.yml). In addition to the dependencies for Dockstore users, note the setup instructions for postgres. Specifically, you will need to have postgres installed and setup with the database user specified in .travis.yml. 

### Building

If you maven build in the root directory this will build not only the web service but the client tool:

    mvn clean install
    
If you have access to our confidential data package for extended testing, you can activate that profile via

    mvn clean install -Pconfidential-tests

### Running Locally

You can also run it on your local computer but will need to setup postgres separately.

1. Fill in the template dockstore.yml and stash it somewhere outside the git repo (like ~/.dockstore)
2. The dockstore.yml is mostly a standard [Dropwizard configuration file](http://www.dropwizard.io/0.9.2/docs/manual/configuration.html). Refer to the linked document to setup httpClient and database. 
3. Start with `java -jar dockstore-webservice/target/dockstore-webservice-*.jar   server ~/.dockstore/dockstore.yml`
4. If you need integration with GitHub.com, Quay.io. or Bitbucket for your work, you will need to follow the appropriate sections below and then fill out the corresponding fields in your [dockstore.yml](https://github.com/ga4gh/dockstore/blob/develop/dockstore.yml#L2). 

### View Swagger UI

The Swagger UI is reachable while the Dockstore webservice is running. This allows you to explore available web resources.

1. Browse to [http://localhost:8080/static/swagger-ui/index.html](http://localhost:8080/static/swagger-ui/index.html)

### Demo Integration with Github.com

Setup your copy of Dockstore as a third-party application able to communicate with GitHub on behalf of a GitHub user. 

1. Setup a new OAuth application at [Register a new OAuth application](https://github.com/settings/applications/new)
2. Browse to [http://localhost:8080/integration.github.com](http://localhost:8080/integration.github.com)
3. Authorize via github.com using the provided link
4. Browse to [http://localhost:8080/github.repo](http://localhost:8080/github.repo) to list repos along with their collab.json (if they exist)

### Demo Integration with Quay.io

Setup your copy of Dockstore as a third-party application able to communicate with quay.io on behalf of a quay.io user. 

1. Setup an application as described in [Creating a new Application](http://docs.quay.io/api/)
2. Browse to [http://localhost:8080/integration.quay.io](http://localhost:8080/integration.quay.io)
3. Authorize via quay.io using the provided link
4. Browse to [http://localhost:8080/container](http://localhost:8080/container) to list repos that we have tokens for at quay.io

### Demo Integration with Bitbucket
 
1. Setup a new application as described in [Integrate another application through OAuth](https://confluence.atlassian.com/bitbucket/integrate-another-application-through-oauth-372605388.html). 
2. Use the dockstore-ui to authorize Bitbucket access for your current logged in user. Use the UI refresh controls to refresh your tools. 

### Webservice Demo

Demo the webservice and test communication with GitHub and quay.io

1. Build the project and run the webservice. NOTE: The webservice will grab and use the IP of the server running the API. For example, if running on a docker container with IP 172.17.0.24, the API will use this for the curl commands and request URLs.
2. Add your Github token. Follow the the steps above to get your Github token. This will create a user with the same username.
3. Add your Quay token. It will automatically be assigned to the user created with Github if the username is the same. If not, you need to user /token/assignEndUser to associate it with the user.
4. To load all your containers from Quay, use /container/refresh to load them in the database for viewing. This needs to be done automatically once the Quay token is set.
5. Now you can see and list your containers. Note that listing Github repos do not return anything because it does not return a valid json.

## Development

### Coding Standards

Please refer to SeqWare's [Coding Standards](https://seqware.github.io/docs/100-coding-standards/). 

### Dockstore Java Client

Some background on the client:

* https://sdngeeks.wordpress.com/2014/08/01/swagger-example-with-java-spring-apache-cxf-jackson/
* http://developers-blog.helloreverb.com/enabling-oauth-with-swagger/

### Dockstore Command Line

The dockstore command line should be installed in a location in your path.

  /dockstore-client/bin/dockstore

You then need to setup a `~/.dockstore/config` file with the following contents:

```
token: <dockstore_token_from_web_app>
server-url: http://www.dockstore.org:8080
```

If you are working with a custom-built or updated dockstore client you will need to update the jar in: `~/.dockstore/config/self-installs`.

### Swagger Java Client for Dockstore

This will no longer be necessary to do manually and is now done as part of the Maven build process.
Just remember to commit a new `dockstore-webservice/src/main/resources/swagger.yaml` when the dockstore API changes. 
Content is left here for reference purposes. 

Background:

 * Client library generated by the [swagger code generator](https://github.com/swagger-api/swagger-codegen)
 * Is generated based on the webservice's swagger UI spec
 * Used by the Dockstore CLI to make http requests to the dockstore
 * If you changed/added some endpoints that the CLI uses, you will need to regenerate the swagger client.
 
To regenerate the swagger client:

1. Have the dockstore webservice running
2. Pull the code from their repo and cd to the directory. We are using v2.1.4. Build using `mvn clean install`
3. Run `java -jar modules/swagger-codegen-cli/target/swagger-codegen-cli.jar generate -i http://localhost:8080/swagger.json -l java -o <output directory> --library jersey2`. The output directory is where you have dockstore/swagger-java-client/.
4. NOTE: Re-generating the swagger client will probably generate an incorrect pom file. Use git checkout on the pom file to undo the changes to it.

### Swagger Java Client for quay.io

This will no longer be necessary to do manually and is now done as part of the Maven build process.
Content is left here for reference purposes. 

Background:

 * Client library generated by the [swagger code generator](https://github.com/swagger-api/swagger-codegen)
 * Is generated based on the quay.io's swagger UI spec
 * Used by the Dockstore CLI to make http requests to quay.io
 * If CoreOS changes their API, you will need to regenerate the swagger client.
 
 To regenerate the swagger client:
 
1. Run `echo "{\"library\":\"jersey2\",\"invokerPackage\":\"io.swagger.quay.client\",\"modelPackage\":\"io.swagger.quay.client.model\",\"apiPackage\":\"io.swagger.quay.client.api\"}" > config.json`
2. Run `java -jar modules/swagger-codegen-cli/target/swagger-codegen-cli.jar generate -i https://quay.io/api/v1/discovery -l java -o <output directory> --library jersey2 --config config.json`. The output directory is where you have dockstore/swagger-java-client/.
3. NOTE: Rengenerating the swagger client will probably generate an incorrect pom file. Use git checkout on the pom file to undo the changes to it.

### Swagger Web Service Components for GA4GH Tool Registry Schema

Background:

 * The [tool-registry-schema](https://github.com/ga4gh/tool-registry-schemas) are intended on allowing different tool registries to exchange and compare data
 * Defined in swagger yaml
 * We use the online swagger editor to generate a JAX-RS skeleton for implementation
 * Unlike the above components, this is a server component rather than client component, thus we cannot use swagger-codegen (client-only for now?)
 
 To regenerate the swagger client:
 
1. Open up the yaml document for the specification in the editor.swagger.io
2. Hit Generate Server and select JAX-RS
3. Replace the appropriate classes in dockstore-webservice
4. Unlike the client classes, we cannot separate quite as cleanly. Classes to watch out for are io.swagger.api.ToolsApi (includes DropWizard specific UnitOfWork annotations and a custom path) and io.swagger.api.impl.ToolsApiServiceImpl (includes our implementation).


### CWL Avro documents

Background:
* The CWL specification is defined in something similar to but not entirely like Avro
* Use the schema salad project to convert to an avro-ish schema document
* Generate the Java classes for the schema
* We cannot use these classes directly since CWL documents are not json or avro binaries, use cwl-tool to convert to json and 
then gson to convert from json due to some incompatibilities between CWL avro and normal avro.  

To regenerate:

1. Get schema salad from the common-workflow-language organization and run `python -mschema_salad --print-avro ~/common-workflow-language/draft-3/cwl-avro.yml`
2. Get the avro tools jar and CWL avsc and call `java -jar avro-tools-1.7.7.jar compile schema cwl.avsc cwl`
3. Copy them to the appropriate directory in dockstore-client (you will need to refactor and insert package names)

Eventually, this will be moved out as a proper Maven dependency on https://github.com/common-workflow-language/cwlavro

### HubFlow Operations

#### How to perform a Maven release for a Unstable Release

This is for pre-release versions that have not been released to production. 

1. Create a release tag and iterate pom file versions `mvn release:prepare`
2. Release from the tag into artifactory (you may need permissions) `mvn release:perform`
3. Merge to master if this is a stable release `git checkout master; git merge <your tag here>`

Special note: If a test is failing during perform, but did not fail during prepare or Travis-CI builds, you may have a non-deterministic error. Skip tests during a release with `mvn release:perform -Darguments="-DskipTests"`

After the release to Artifactory, document the release on GitHub via the Releases page. Take a look at commits since the last release and closed pull requests for information on what goes into the changelog. Also attach the newly created Dockstore script.

### How to perform a Maven release for an Stable Release

This is for release versions that have been released to production. 


### Encrypted Documents for Travis-CI

Encrypted documents necessary for confidential testing are handled as indicated in the documents at Travis-CI for  
[files](https://docs.travis-ci.com/user/encrypting-files/#Encrypting-multiple-files) and [environment variables](https://docs.travis-ci.com/user/encryption-keys).

A convenience script is provided as encrypt.sh which will compress confidential files, encrypt them, and then update an encrypted archive on GitHub. Confidential files should also be added to .gitignore to prevent accidental check-in. The unencrypted secrets.tar should be privately distributed among members of the team that need to work with confidential data. 

To dump a new copy of the encrypted database from one that you have setup, use the following (or similar):

    pg_dump --data-only --column-inserts   webservice_test &> dockstore-integration-testing/src/test/resources/db_confidential_dump_full.sql


## Adding Copyright header to all files with IntelliJ

To add copyright headers to all files with IntelliJ

1. Ensure the Copyright plugin is installed (Settings -> Plugins)
2. Create a new copyright profile matching existing copyright header found on all files, name it Dockstore (Settings -> Copyright -> Copyright Profiles -> Add New)
3. Set the default project copyright to Dockstore (Settings -> Copyright)

## Work-In-Progress

### Build Docker Version

This is still a work in progress. However, it is possible to setup and run Dockstore itself in a container.

    docker build -t dockstore:1.0.0 .

### Running Via Docker

Probably the best way to run this since it includes a bundled postgres.  Keep in mind once you terminate the Docker container any state in the DB is lost.

1. Fill in the template dockstore.yml and stash it somewhere outside the git repo (like ~/.dockstore)
2. Start with `docker run -it -v ~/.dockstore/dockstore.yml:/dockstore.yml -e POSTGRES_PASSWORD=iAMs00perSecrEET -e POSTGRES_USER=webservice -p 8080:8080 dockstore:1.0.0`

You can also run with defaults using

1. `docker run -P -ti --rm dockstore`
