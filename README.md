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
3. Authorize via quay.io 
4. Browse to [localhost:8080/docker.repo](localhost:8080/docker.repo) to list repos that we have tokens for at quay.io
