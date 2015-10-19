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
2. Start with `java -jar dockstore-webservice/target/dockstore-webservice-*.jar   server ~/.dockstore/hello-world.yml`

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
2. Build the project and run the webservice.
3. Add your Github token. Follow the the steps above to get your Github token. This will create a user with the same username.
4. Add your Quay token. It will automatically be assigned to the user created with Github if the username is the same. If not, you need to user /token/assignEndUser to associate it with the user.
5. To load all your containers from Quay, use /container/refresh to load them in the database for viewing. This needs to be done automatically once the Quay token is set.
6. Now you can see and list your containers. Note that listing Github repos do not return anything because it does not return a valid json.

### Temporary Hack to List Containers

Recently, Quay has fixed/optimized their API. To list a user's repositories, the required parameters: namespace, starred or public will have to be specified. If you do not specify namespace, you will see other people's public repositories, which is not what we want. Therefore we need to have namespace.

However, the only way to list all namespaces and organizations is to use their /api/v1/user/ resource. But this resource seems to have a bug and does not list the organizations. Therefore, we are temporarily hard coding our namespaces to DockerRepoResource.java.

## TODO

1. we need to define how this interacts with a single sign-on service
   1. in general, users should be able to list their own information (such as tokens and repos)
   2. only admin users (or our other services) should be able to list all information  
1. items from Brian
   2. you need better directions for filling in the yml settings file
   3. you should create a Dockerfile for this so I can deploy with Docker, this will make testing much easier
