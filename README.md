# dockstore

This is the prototype web service for the dockstore. The usage of this is to enumerate the docker containers (from quay.io and hopefully docker hub) and the workflows (from github) that are available to users of Collaboratory.

## Usage

### Build Docker Version

  docker build -t dockstore:1.0.0 .

### Running Via Docker

Probably the best way to run this since it includes a bundled postgres.  Keep in mind once you terminate the Docker container
any state in the DB is lost.

1. Fill in the template hello-world.yml and stash it somewhere outside the git repo (like ~/.dockstore)
2. Start with `docker run -it -v ~/.dockstore/hello-world.yml:/hello-world.yml -e POSTGRES_PASSWORD=iAMs00perSecrEET -e POSTGRES_USER=webservice -p 8080:8080 dockstore:1.0.0`

### Running Locally

You can also run it on your local computer but will need to setup postgres separately.

1. Fill in the template hello-world.yml and stash it somewhere outside the git repo (like ~/.dockstore)
2. Start with `java -jar dockstore-webservice/target/dockstore-0.0.1-SNAPSHOT.jar server ~/.dockstore/hello-world.yml`

### View Swagger UI

1. Browse to [http://localhost:8080/static/swagger-ui/index.html](http://localhost:8080/static/swagger-ui/index.html)

### Demo Integration with Quay.io

1. Setup an application as described in [Creating a new Application](http://docs.quay.io/api/)
2. Browse to [http://localhost:8080/integration.quay.io](http://localhost:8080/integration.quay.io)
3. Authorize via quay.io using the provided link
4. Browse to [http://localhost:8080/docker.repo](http://localhost:8080/docker.repo) to list repos that we have tokens for at quay.io

### Demo Integration with Github.com

1. Setup a new OAuth application at [Register a new OAuth application](https://github.com/settings/applications/new)
2. Browse to [http://localhost:8080/integration.github.com](http://localhost:8080/integration.github.com)
3. Authorize via github.com using the provided link
4. Browse to [http://localhost:8080/github.repo](http://localhost:8080/github.repo) to list repos along with their collab.json (if they exist)

## TODO

1. we need to define how this interacts with a single sign-on service
   1. in general, users should be able to list their own information (such as tokens and repos)
   2. only admin users (or our other services) should be able to list all information  
1. items from Brian
   2. you need better directions for filling in the yml settings file
   3. you should create a Dockerfile for this so I can deploy with Docker, this will make testing much easier
