# Release Horreum

## Creating a new stable branch
This step is not required if you are going to perform a micro release from an already existing stable branch, e.g., `0.17.5`. 

Open https://github.com/Hyperfoil/Horreum/actions/workflows/branch.yml and run the workflow. Select the `master` branch.

## Perform Release

Open https://github.com/Hyperfoil/Horreum/actions/workflows/release.yml and run the workflow. Select the previous branch created.

This workflow will take care of releasing all artifacts and container images on the appropriate registries:
* Create a new GitHub tag following the semantic versioning (e.g., 0.17.0-SNAPSHOT → 0.17.0)
* Push the maven artifacts to [Sonatype](https://s01.oss.sonatype.org/#nexus-search;quick~horreum)
* Push the generated container image to [quay.io/hyperfoil/horreum](https://quay.io/repository/hyperfoil/horreum)

The only missing step is the creation of the GitHub release, a step that at the moment needs to be done manually using the GitHub UI. Consider using the “Generate release notes” feature to pre-populate the release notes automatically after selecting the correct tags.

# Release Horreum clients

After testing the new release or release candidate, a new version of each client must be created by following the `RELEASE.md` document in each repository

* https://github.com/Hyperfoil/horreum-client-golang
* * https://github.com/Hyperfoil/horreum-client-golang/blob/main/docs/RELEASE.md
* https://github.com/Hyperfoil/horreum-client-python
* * https://github.com/Hyperfoil/horreum-client-python/blob/main/docs/RELEASE.md
