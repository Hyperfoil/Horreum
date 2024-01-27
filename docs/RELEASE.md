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

Backports are automatically created from PRs against the master branch to the stable branch.

To backport a change, add the `backport` label to the PR that you opened against the master branch.

After the PR is merged, the backport PR will be automatically created.

Backport PRs will be tested by the CI and need to be merged manually.

## Building a release

Make sure that the [nodejs](https://nodejs.org/en) installation is clean,

```bash
mvn -Premove-node-cache clean
git checkout horreum-web/package-lock.json
```

Then run the [Maven](https://maven.apache.org/) release procedure

```bash
mvn -DskipTests clean javadoc:javadoc install
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

## Creating a new Stable Branch

To create a new stable branch, for example creating a `0.11.x` branch, first create the branch locally:

```bash
git checkout origin/master
git checkout -b 0.11.x
git checkout origin/master
```

Update the new main branch to the next snapshot version:

```bash
mvn versions:set -DnewVersion=0.12-SNAPSHOT
```

Update the openapi version in the `horreum-api/pom.xml` file:

```xml
	<openapi-version>0.12</openapi-version>
```

Update the `horreum-api/src/main/java/io/hyperfoil/tools/horreum/api/Version.java` file

```java
public class Version {
   public static final String VERSION = "0.12.0";
}
```

Commit the changes:

```bash
git add .
git commit -m "Next is 0.12"
```

Push the new branch and main branch to github:
    
```bash
git push origin 0.11.x
git push origin master
```

