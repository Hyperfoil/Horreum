# Horreum

Horreum is a service for storing performance data and regression analysis.

Project website: [https://horreum.hyperfoil.io](https://horreum.hyperfoil.io)

## Prerequisites

* [Java 11](https://adoptium.net/temurin/releases/?version=11)
* [Apache Maven 3.8](https://maven.apache.org/)
* [Keycloak](https://www.keycloak.org/)
* [Grafana](https://grafana.com/)
* [PostgreSQL 12+](https://www.postgresql.org/)

### Docker

We have prepared a `docker-compose` script to setup Keycloak, PostgreSQL and Grafana using

```bash
docker-compose -p horreum -f infra/docker-compose.yml up -d
```
and after a few moments everything should be up and ready. The script will create some example users.

### podman

We have prepared a `podman-compose` script to setup Keycloak, PostgreSQL and Grafana using

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

### OpenSSL

Horreum currently depends on the OpenSSL 1.1.x API so you will need to enable legacy mode if you are
using an OpenSSL 3 platform.

To do this edit the configuration file `/etc/ssl/openssl.cnf` to uncomment this section

``` openssl.conf
[provider_sect]
default = default_sect
legacy = legacy_sect

[default_sect]
activate = 1

[legacy_sect]
activate = 1
```

and set the following environment variable

``` bash
export NODE_OPTIONS=--openssl-legacy-provider
```

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

Access

* [localhost:3000](http://localhost:3000) for the create-react-app live code server
* [localhost:8080](http://localhost:8080) for the Quarkus development server

### Troubleshooting development infrastructure

If PostgreSQL container fails to start try removing the volume using:

```bash
podman volume rm horreum_horreum_pg12
```

If you are having problems with Grafana login after restarting the infrastructure wipe out old environment files using:

```bash
rm horreum-backend/.env .grafana
```

## Operator

The [Horreum operator](https://github.com/Hyperfoil/horreum-operator) can help to setup a production environment.

## Contributing

Contributions to `Horreum` are managed on [GitHub.com](https://github.com/Hyperfoil/Horreum/)

* [Ask a question](https://github.com/Hyperfoil/Horreum/discussions)
* [Raise an issue](https://github.com/Hyperfoil/Horreum/issues)
* [Feature request](https://github.com/Hyperfoil/Horreum/issues)
* [Code submission](https://github.com/Hyperfoil/Horreum/pulls)

Contributions are most welcome !

Please, consult our [Code of Conduct](./CODE_OF_CONDUCT.md) policies for interacting in our
community.

Consider giving the project a [star](https://github.com/Hyperfoil/Horreum/stargazers) on
[GitHub](https://github.com/Hyperfoil/Horreum/) if you find it useful.

## License

[Apache-2.0 license](https://opensource.org/licenses/Apache-2.0)
