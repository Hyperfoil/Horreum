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

To backport a change, add the `backport` (or `backport-squash` but not both) label to the PR that you opened against the master branch.

Which label should I use?
* `backport`: (default) this uses the `no-squash=true` option so that the tool tries to backport all commits coming
from the original pull request you are trying to backport.
> _**Note**_ that in this case the commit SHAs should exist during the backporting, i.e,
delete the source branch only after the backporting PR got created.
* `backport-squash`: with this label you set `no-squash=false` option, so that the tool tries to backport the pull request
`merge_commit_sha`.
> _**Note**_ the value of the `merge_commit_sha` attribute changes depending on the state of the pull request, see [Github doc](https://docs.github.com/en/rest/pulls/pulls?apiVersion=2022-11-28#get-a-pull-request)
  for more details.

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
CI=true mvn -DskipTests clean javadoc:javadoc install
CI=true mvn -Darguments=-DskipTests release:prepare
CI=true mvn -Darguments=-DskipTests release:perform
```

Now we can build the tag

```bash
git checkout <tag>
CI=true mvn -DskipTests -DskipITs clean install
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

Generate release notes:

https://github.com/Hyperfoil/Horreum/releases

## Creating a new Stable Branch

To create a new stable branch, for example creating a `0.11.x` branch, first create the branch locally:

```bash
git checkout origin/master
git branch 0.11.x master
```

Update the new main branch to the next snapshot version:

```bash
mvn versions:set -DnewVersion=0.12-SNAPSHOT
mvn versions:set-property -Dproperty=major-version -DnewVersion=0.12
```

Update the Github actions to build the new stable branch for each push:

```yaml
on:
  push:
    branches: [ master, 0.11.x ]
    tags: [ "*" ]
  pull_request:
  workflow_dispatch:
```

Update the Github action to notify openapi changes to the clients on every stable branch push:
```yaml
on:
  push:
    branches: [ master, 0.11.x ]
    paths:
      - "docs/site/content/en/openapi/openapi.yaml"
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

