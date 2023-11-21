# Release

## Building a base image

You can build a base image for the Horreum distribution with

```bash
cd horreum-backend/
podman build -f src/main/docker/Dockerfile.jvm.base -t quay.io/hyperfoil/horreum-base:latest .
```

Then it can be pushed to [quay.io](https://quay.io/) with

```bash
podman push quay.io/hyperfoil/horreum-base:latest
```

if the proper credentials are in place.

## quay.io and podman

Login to [quay.io](https://quay.io/) using [podman](https://podman.io/)

```bash
podman login quay.io
```

Logout of [quay.io](https://quay.io/) using [podman](https://podman.io/)

```bash
podman logout quay.io
```

## Building a release

Make sure that the [nodejs](https://nodejs.org/en) installation is clean,

```bash
mvn -Premove-node-cache clean
git checkout horreum-web/package-lock.json
```

Then run the [Maven](https://maven.apache.org/) release procedure

```bash
mvn -DskipTests clean install
mvn -Darguments=-DskipTests release:prepare
mvn -Darguments=-DskipTests release:perform
```

Now we can build the tag

```bash
git checkout <tag>
mvn -DskipTests -DskipITs clean install
```

and find the image using

```bash
podman images
```

and tag it to the release

```bash
podman tag quay.io/hyperfoil/horreum:<tag> quay.io/hyperfoil/horreum:<release>
podman tag quay.io/hyperfoil/horreum:<release> quay.io/hyperfoil/horreum:latest
```

and finally push them

```bash
podman push quay.io/hyperfoil/horreum:<release>
podman push quay.io/hyperfoil/horreum:latest
```
