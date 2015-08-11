# dockstore

## dropwizard\_swagger

Personal experiment with dropwizard 0.8.2 + swagger 1.5.0

## Usage

### Starting Up

1. Fill in the template hello-world.yml and stash it somewhere outside the git repo (like ~/.stash)
2. Start with java -jar target/guqin-0.0.1-SNAPSHOT.jar server ~/.stash/hello-world.yml

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
