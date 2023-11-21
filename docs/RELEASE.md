# Release

## Building a base image

You can build a base image for the Horreum distribution with

```bash
cd horreum-backend/
docker build -f src/main/docker/Dockerfile.jvm.base -t quay.io/hyperfoil/horreum-base:latest .
```

Then it can be pushed to [quay.io](https://quay.io/) with

```bash
docker push quay.io/hyperfoil/horreum-base:latest
```

if the proper credentials are in place.

## quay.io and docker

Login to [quay.io](https://quay.io/) using [docker](https://docker.io/)

```bash
docker login quay.io
```

Logout of [quay.io](https://quay.io/) using [docker](https://docker.io/)

```bash
docker logout quay.io
```

## Backports

Backports are done off the `master` branch using `git cherry-pick` so

```
git log
git checkout 0.10.x
git cherry-pick <tag>
git push origin 0.10.x
```

e.g. find the commit in question (bug fix or enhancement), checkout the latest stable branch, then cherry-pick it in and push.

This should be straight forward as there are no merge commits in `master`.

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
docker images
```

and tag it to the release

```bash
docker tag quay.io/hyperfoil/horreum:<tag> quay.io/hyperfoil/horreum:<release>
docker tag quay.io/hyperfoil/horreum:<release> quay.io/hyperfoil/horreum:latest
```

and finally push them

```bash
docker push quay.io/hyperfoil/horreum:<release>
docker push quay.io/hyperfoil/horreum:latest
```

## After the release

The version identifier for a release is from

```
./horreum-api/src/main/java/io/hyperfoil/tools/horreum/api/Version.java
```

so that file needs to be updated after a release.
