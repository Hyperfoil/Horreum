<div align="center">

# Horreum

 <a href="https://horreum.hyperfoil.io/"><img alt="Website" src="https://img.shields.io/website?up_message=live&url=https%3A%2F%2Fhorreum.hyperfoil.io/"></a>
<a href="https://github.com/Hyperfoil/Horreum/issues"><img alt="GitHub issues" src="https://img.shields.io/github/issues/Hyperfoil/Horreum"></a>
<a href="https://github.com/Hyperfoil/Horreum/fork"><img alt="GitHub forks" src="https://img.shields.io/github/forks/Hyperfoil/Horreum"></a>
<a href="https://github.com/Hyperfoil/Horreum/stargazers"><img alt="GitHub stars" src="https://img.shields.io/github/stars/Hyperfoil/Horreum"></a>
<a href="https://github.com/Hyperfoil/Horreum//blob/main/LICENSE"><img alt="GitHub license" src="https://img.shields.io/github/license/Hyperfoil/Horreum"></a> 
</div>

---
## What is Horreum?

Horreum is a service for storing performance data and regression analysis.

Please, visit our project website: 

[https://horreum.hyperfoil.io](https://horreum.hyperfoil.io)

for more information.

Horreum is a [Quarkus](https://quarkus.io/) based application which uses
[Quinoa](https://quarkiverse.github.io/quarkiverse-docs/quarkus-quinoa/dev/) as its [nodejs](https://nodejs.org/en) engine.

## Prerequisites

* [Java 11](https://adoptium.net/temurin/releases/?version=11)
* [Apache Maven 3.8](https://maven.apache.org/)
* [Keycloak](https://www.keycloak.org/)
* [PostgreSQL 12+](https://www.postgresql.org/)

### Local development in Windows
We have prepared a seperate `docker-compose` script to setup Keycloak and PostgreSQL in the Windows platform, check the guide 👉 [Local development in Windows](./doc/Windows.md)


### Local development with Docker Compose

We have prepared a `docker-compose` script to setup Keycloak and PostgreSQL using following command.

```bash
docker-compose -p horreum -f infra/docker-compose.yml up -d
```
and after a few moments everything should be up and ready. The script will create some example users.

### Local development with Podman

We have prepared a `podman-compose` script to setup Keycloak and PostgreSQL using following command.

```bash
./infra/podman-compose.sh
```

and after a few moments everything should be up and ready. The script will create some example users.

Install:

``` bash
dnf install -y podman podman-plugins podman-compose podman-docker
```

Please, enable the socket environment in order to run the test suite:

``` bash
systemctl --user enable --now podman.socket
export DOCKER_HOST=unix:///run/user/${UID}/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true
```

Shutdown:

``` bash
podman-compose -p horreum -f infra/docker-compose.yml down
```

### Example configuration

You can preload Horreum with some example data with

```bash
./infra/example-configuration.sh
```

once Horreum is running.

## Credentials

### Horreum

Horreum is running on [localhost:8080](http://localhost:8080)

| Role | Name | Password |
| ---- | ---- | -------- |
| User | `user` | `secret` |


### Keycloak

Keycloak is running on [localhost:8180](http://localhost:8180)

| Role | Name | Password | Realm |
| ---- | ---- | -------- | ----- |
| Admin | `admin` | `secret` | |
| User | `user` | `secret` | `horreum` |

## Getting Started with development server

To run with test cases do

```bash
mvn package
mvn quarkus:dev
```

To run without test cases do

```bash
mvn -DskipTests=true package
mvn -Dquarkus.test.continuous-testing=disabled quarkus:dev
```

## Get Access

* For the create-react-app live code server [localhost:3000](http://localhost:3000)
* For the Quarkus development code server   [localhost:8080](http://localhost:8080)

### Troubleshooting development infrastructure

If PostgreSQL container fails to start try removing the volume using:

```bash
podman volume rm horreum_horreum_pg13
```

## Tested platforms

* Linux (Fedora, RHEL)
* Windows/WSL2 (Windows 10 and Windows 11)

## Operator

The [Horreum operator](https://github.com/Hyperfoil/horreum-operator) can help to setup a production environment.

## 🧑‍💻 Contributing

Contributions to `Horreum` Please check our [CONTRIBUTING.md](./CONTRIBUTING.md)

### If you have any idea or doubt 👇

* [Ask a question](https://github.com/Hyperfoil/Horreum/discussions)
* [Raise an issue](https://github.com/Hyperfoil/Horreum/issues)
* [Feature request](https://github.com/Hyperfoil/Horreum/issues)
* [Code submission](https://github.com/Hyperfoil/Horreum/pulls)

Contribution is the best way to support and get involved in community !

Please, consult our [Code of Conduct](./CODE_OF_CONDUCT.md) policies for interacting in our
community.

Consider giving the project a [star](https://github.com/Hyperfoil/Horreum/stargazers) on
[GitHub](https://github.com/Hyperfoil/Horreum/) if you find it useful.

## License

[Apache-2.0 license](https://opensource.org/licenses/Apache-2.0)

## Thanks to all the Contributors ❤️

<img src="https://contrib.rocks/image?repo=Hyperfoil/Horreum" />
