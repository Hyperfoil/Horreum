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
<table>
  <tbody>
    <tr>     
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/jesperpedersen"><img src="https://avatars.githubusercontent.com/u/229465?v=4" width="100px;" alt="Jesper Pedersen"/><br /><sub><b>Jesper Pedersen</b></sub></a><br /><a href="https://github.com/Hyperfoil/Horreum/commits?author=jesperpedersen" title="Code">💻</a> <a href="#maintenance-jesperpedersen" title="Maintenance">🚧</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/johnaohara"><img src="https://avatars.githubusercontent.com/u/959822?v=4?s=100" width="100px;" alt="John O'Hara"/><br /><sub><b>John O'Hara</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-quinoa/commits?author=johnaohara" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/shivam-sharma7"><img src="https://avatars.githubusercontent.com/u/91419219?v=4" width="100px;" alt="Shivam Sharma"/><br /><sub><b>Shivam Sharma</b></sub></a><br /><a href="https://github.com/Hyperfoil/Horreum/commits?author=shivam-sharma7" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/stalep"><img src="https://avatars.githubusercontent.com/u/49780?v=4" width="100px;" alt="Stale Pedersen"/><br /><sub><b>Stale Pedersen</b></sub></a><br /><a href="https://github.com/Hyperfoil/Horreum/commits?author=stalep" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/barreiro"><img src="https://avatars.githubusercontent.com/u/856614?v=4" width="100px;" alt="Luis Barreiro"/><br /><sub><b>Luis Barreiro</b></sub></a><br /><a href="https://github.com/Hyperfoil/horreum-operator/commits?author=barreiro" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/whitingjr"><img src="https://avatars.githubusercontent.com/u/708428?v=4" width="100px;" alt="willr3"/><br /><sub><b>Jeremy Whiting</b></sub></a><br /><a href="https://github.com/Hyperfoil/Horreum/commits?author=whitingjr" title="Code">💻</a></td>
        <td align="center" valign="top" width="14.28%"><a href="https://github.com/willr3"><img src="https://avatars.githubusercontent.com/u/1083859?v=4" width="100px;" alt="willr3"/><br /><sub><b>willr3</b></sub></a><br /><a href="https://github.com/Hyperfoil/qDup/commits?author=willr3" title="Code">💻</a></td>
    </tr>
       
  </tbody>
</table>
