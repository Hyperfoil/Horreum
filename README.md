<div align="center">

# Horreum
[![Java CI with Maven](https://github.com/Hyperfoil/Horreum/actions/workflows/main.yml/badge.svg)](https://github.com/Hyperfoil/Horreum/actions/workflows/main.yml)
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

* [Java 17](https://adoptium.net/temurin/releases/?version=17)
* [Apache Maven 3.8](https://maven.apache.org/)
* [Docker](https://www.docker.com/)

## Getting Started with development server

To run with test cases do

```bash
mvn install
mvn quarkus:dev -pl 'horreum-backend'
```

To run without test cases do

```bash
mvn -DskipTests=true -DskipITs install
mvn -Dquarkus.test.continuous-testing=disabled quarkus:dev -pl 'horreum-backend'
```

## Credentials

Horreum is running on [localhost:8080](http://localhost:8080)

| Role | Name | Password |
| ---- | ---- | -------- |
| User | `user` | `secret` |


## Tested platforms

* Linux (Fedora, RHEL)
* Windows/WSL2 (Windows 10 and Windows 11)
* MacOS (13.3) on M2 hardware 

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
