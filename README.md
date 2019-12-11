# Hyperfoil-repo
A Proof of Concept service for storing performance data in postgres.
Expect breaking changes around every turn.

## Prerequisites
```bash
git clone https://github.com/Hyperfoil/yaup.git
cd yaup
mvn clean install

docker volume create repo_pg12
docker run -d --name repo_pg12 -v repo_pg12:/var/lib/postgresql/data -e POSTGRES_DB=repo -e POSTGRES_USER=repo -e POSTGRES_PASSWORD=repo -p 5432:5432 postgres:12
```

## Getting Started
```bash
./mvnw compile quarkus:dev -Dui.dev
```

`localhost:3000` to access the create-react-app live code server and `localhost:8080` to access the quarkus development server.


