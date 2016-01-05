[![Build Status](https://travis-ci.org/ga4gh/dockstore.svg?branch=develop)](https://travis-ci.org/CancerCollaboratory/dockstore)

# Dockstore

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/common-workflow-language/common-workflow-language?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

The Dockstore concept is simple, provide a place where users can share tools encapsulated in Docker and described with the Common Workflow Language (CWL) which is being recommended by the GA4GH Containers and Workflow group. This enables scientists, for example, to share analytical tools in a way that makes them machine readable and runnable in a variety of environments (SevenBridges, Toil, etc). While the Dockstore is focused on serving researchers in the biosciences the combination of Docker + CWL can be used by anyone to describe the tools and services in their Docker images in a standardized, machine-readable way.  We hope to use this project as motivation to create a GA4GH API standard for container registries and intend on making Dockstore fully compliant.

For a live demo see https://dockstore.org

This repo is the web service for the Dockstore. The usage of this is to enumerate the docker containers (from quay.io and hopefully docker hub) and the workflows (from github) that are available to users of Dockstore.org.

For the related web UI see the [dockstore-ui](https://github.com/ga4gh/dockstore-ui) project.

## Usage

### Building

If you maven build in the root directory this will build not only the web service but the client tool:

    mvn clean install

As a pre-requisite, you will need to have postgres installed and setup with the database user specified in .travis.yml. 

### Build Docker Version

    docker build -t dockstore:1.0.0 .

### Running Via Docker

Probably the best way to run this since it includes a bundled postgres.  Keep in mind once you terminate the Docker container
any state in the DB is lost.

1. Fill in the template dockstore.yml and stash it somewhere outside the git repo (like ~/.dockstore)
2. Start with `docker run -it -v ~/.dockstore/dockstore.yml:/dockstore.yml -e POSTGRES_PASSWORD=iAMs00perSecrEET -e POSTGRES_USER=webservice -p 8080:8080 dockstore:1.0.0`

You can also run with defaults using

1. `docker run -P -ti --rm dockstore`

### Running Locally

You can also run it on your local computer but will need to setup postgres separately.

1. Fill in the template dockstore.yml and stash it somewhere outside the git repo (like ~/.dockstore)
2. Start with `java -jar dockstore-webservice/target/dockstore-webservice-*.jar   server ~/.dockstore/dockstore.yml`

### View Swagger UI

1. Browse to [http://localhost:8080/static/swagger-ui/index.html](http://localhost:8080/static/swagger-ui/index.html)

### Demo Integration with Github.com

1. Setup a new OAuth application at [Register a new OAuth application](https://github.com/settings/applications/new)
2. Browse to [http://localhost:8080/integration.github.com](http://localhost:8080/integration.github.com)
3. Authorize via github.com using the provided link
4. Browse to [http://localhost:8080/github.repo](http://localhost:8080/github.repo) to list repos along with their collab.json (if they exist)

### Demo Integration with Quay.io

1. Setup an application as described in [Creating a new Application](http://docs.quay.io/api/)
2. Browse to [http://localhost:8080/integration.quay.io](http://localhost:8080/integration.quay.io)
3. Authorize via quay.io using the provided link
4. Browse to [http://localhost:8080/container](http://localhost:8080/container) to list repos that we have tokens for at quay.io

### Webservice Demo

1. ~~First add all your organizations/namespaces you are associated to on Quay.io to the constructor of `dockstore/dockstore-webservice/src/main/java/io/dockstore/webservice/resources/DockerRepoResource.java`. See next section for details.~~ The webservice will now only use user's Quay username as namespace. This means that you will see only your own Quay repositories.
2. Build the project and run the webservice. NOTE: The webservice will grab and use the IP of the server running the API. For example, if running on a docker container with IP 172.17.0.24, the API will use this for the curl commands and request URLs.
3. Add your Github token. Follow the the steps above to get your Github token. This will create a user with the same username.
4. Add your Quay token. It will automatically be assigned to the user created with Github if the username is the same. If not, you need to user /token/assignEndUser to associate it with the user.
5. To load all your containers from Quay, use /container/refresh to load them in the database for viewing. This needs to be done automatically once the Quay token is set.
6. Now you can see and list your containers. Note that listing Github repos do not return anything because it does not return a valid json.

## Coding Standards

Please refer to SeqWare's [Coding Standards](https://seqware.github.io/docs/100-coding-standards/). 

## Dockstore Java Client

Some background on the client:

* https://sdngeeks.wordpress.com/2014/08/01/swagger-example-with-java-spring-apache-cxf-jackson/
* http://developers-blog.helloreverb.com/enabling-oauth-with-swagger/

## Dockstore Command Line

The dockstore command line should be installed in a location in your path.

  /dockstore-client/bin/dockstore

You then need to setup a `~/.dockstore/config` file with the following contents:

```
token: <dockstore_token_from_web_app>
server-url: http://www.dockstore.org:8080
```

If you are working with a custom-built or updated dockstore client you will need to update the jar in: `~/.dockstore/config/self-installs`.

## Swagger Java Client for Dockstore
Background:

 * Client library generated by the [swagger code generator](https://github.com/swagger-api/swagger-codegen)
 * Is generated based on the webservice's swagger UI spec
 * Used by the Dockstore CLI to make http requests to the dockstore
 * If you changed/added some endpoints that the CLI uses, you will need to regenerate the swagger client.
 
To regenerate the swagger client:

1. Have the dockstore webservice running
2. Pull the code from their repo and cd to the directory. Build using `mvn package`
3. Run `java -jar modules/swagger-codegen-cli/target/swagger-codegen-cli.jar generate -i http://localhost:8080/swagger.json -l java -o <output directory> --library jersey2`. The output directory is where you have dockstore/swagger-java-client/.
4. NOTE: Rengenerating the swagger client will probably generate an incorrect pom file. Use git checkout on the pom file to undo the changes to it.

## Swagger Java Client for quay.io

Background:

 * Client library generated by the [swagger code generator](https://github.com/swagger-api/swagger-codegen)
 * Is generated based on the quay.io's swagger UI spec
 * Used by the Dockstore CLI to make http requests to quay.io
 * If CoreOS changes their API, you will need to regenerate the swagger client.
 
 To regenerate the swagger client:
 
1. Run `echo "{\"library\":\"jersey2\",\"invokerPackage\":\"io.swagger.quay.client\",\"modelPackage\":\"io.swagger.quay.client.model\",\"apiPackage\":\"io.swagger.quay.client.api\"}" > config.json`
2. Run `java -jar modules/swagger-codegen-cli/target/swagger-codegen-cli.jar generate -i https://quay.io/api/v1/discovery -l java -o <output directory> --library jersey2`. The output directory is where you have dockstore/swagger-java-client/.
3. NOTE: Rengenerating the swagger client will probably generate an incorrect pom file. Use git checkout on the pom file to undo the changes to it.


## CWL Avro documents

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

## How to perform a Maven release 

Where 0.2.2 should be modified to the version number of your next release

1. Start a release branch `git hf release start 0.2.2`
2. Iterate the verion numbers for your Maven pom files `mvn versions:set -DnewVersion=0.2.2`
3. Check that everything still builds and tests properly `mvn clean install -DskipITs=false`
4. Finish the release (which creates a tag) `git hf release finish 0.2.2`. Accept proposed merges to develop and master if they look reasonable. 
5. Use the maven release plugin to perform the release (due to a bug, use maven-release-plugin 2.3.2) or failing that upload manually to artifactory `mvn release:perform -DconnectionUrl=scm:git:git@github.com:ga4gh/dockstore.git -Dtag=0.2.2 `
7. Remember to iterate the version numbers on the develop branch to the snapshot version of your next release `mvn versions:set -DnewVersion=0.2.3-SNAPSHOT ; git add pom.xml \*/pom.xml ; git push`
8. Fiddle with github releases and update docs

## Encrypted Documents for Travis-CI

Encrypted documents necessary for confidential testing are handled as indicated in the documents at Travis-CI for  
[https://docs.travis-ci.com/user/encrypting-files/#Encrypting-multiple-files](files) and [https://docs.travis-ci.com/user/encryption-keys/](environment variables). The contents of the files should be described (but not stored obviously) so that future developers can re-create them as necessary. 




## TODO

1. items from Brian
   2. you need better directions for filling in the yml settings file
1. you need to document the config file
1. you need to document the release process, how to update the jar the dockstore command line downloads
