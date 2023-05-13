## Local development with Windows in WSL 2 environment
First you have to make sure, you are running Docker desktop in windows. See [Docker Desktop](https://www.docker.com/products/docker-desktop/) for more information.

 
### 1. Build with the docker-compose command
```bash
 docker compose -p horreum -f infra/docker-compose.yml up -d
```

 It will take some time to pull all images so after running above command you can see that you have pulled all images from docker hub.

 
## Getting Started with development server

### 2. Run  test cases
```
mvn clean install -DkipTest -DskipITs -Dquarkus.quinoa=false
```
 It will take some time, so the Apache Maven can see if the build is successful
 
### 3. The development server command

```
mvn quarkus:dev -Dquarkus.quinoa=false
```

### 4. Access the development server
* This is the port for the application [localhost:8080](http://localhost:8080)
