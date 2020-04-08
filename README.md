# Hyperfoil Horreum
A Proof of Concept service for storing performance data in postgres.
Expect breaking changes around every turn.

## Prerequisites
```bash
docker volume create repo_pg12
docker run -d --name repo_pg12 -v repo_pg12:/var/lib/postgresql/data -e POSTGRES_DB=repo -e POSTGRES_USER=repo -e POSTGRES_PASSWORD=repo -p 5432:5432 postgres:12
psql -h localhost -U repo -f repo/src/main/resources/structure.sql
# Replace password and secret passphrase (repo.db.secret in application properties) below
psql -h localhost -U repo -c "create database keycloak; create role repo_restricted noinherit login password 'repo'; insert into dbsecret (passphrase) values ('secret');"
psql -h localhost -U repo -f repo/src/main/resources/policies.sql
psql -h localhost -U repo -f repo/src/main/resources/permissions.sql
docker run -d --name keycloak -e KEYCLOAK_USER=admin -e KEYCLOAK_PASSWORD=admin -e DB_VENDOR=postgres -e DB_ADDR=172.17.0.1 -e DB_USER=repo -e DB_PASSWORD=repo -p 8180:8080 jboss/keycloak
```

Now that the database is running, it's time to configure Keycloak. Open [localhost:8180](http://localhost:8180) and login using credentials `admin`/`admin`.
Import `repo/src/main/resources/keycloak-horreum.json` with realm `horreum`. The only defined user `user` has password `secret`; you'll use that one to login into UI.                                                                                       

## Getting Started
```bash
cd repo/webapp
npm install
cd ..
./mvnw compile quarkus:dev -Dui.dev
```

`localhost:3000` to access the create-react-app live code server and `localhost:8080` to access the quarkus development server.

## Creating jar
```bash
./mvnw clean package -Dui
```
This builds the webapp and adds it to the `repo-${version}-runner.jar`.
Start the server with `java -jar repo-${version}-runner.jar` after docker is already running

## native mode
###does not work~
```bash
./mvnw package -Pnative -Dui
```
This _should_ build the native image but it fails
```
java.lang.NoClassDefFoundError: com/sun/jna/LastErrorException
```

## Security

Security uses RBAC with authz and authn provided by Keycloak server, and heavily relies on row-level security (RLS) in the database.
The should be two DB users (roles); `repo` who has full access to the database, and `repo_restricted` with limited access.
`repo` should set up DB structure - tables with RLS policies and grant RW access to all tables but `dbsecret` to `repo_restricted`.
When the application performs a database query, impersonating the authenticated user, it invokes `SET repo.userroles = '...'`
to declare all roles the user has based on information from Keycloak. RLS policies makes sure that the user cannot read or modify
anything that does not belong to this user or is made available to him.

As a precaution against bug leaving SQL-level access open the `repo.userroles` granting the permission are not set in plaintext;
the format of the setting is `role1:seed1:hash1,role2:seed2:hash2,...` where the `hash` is SHA-256 of combination of role, seed
and hidden passphrase. This passphrase is set in `application.properties` under key `repo.db.secret`, and in database as the only
record in table `dbsecret`. The user `repo_restricted` does not have access to that table, but the security-defined functions used
in table policies can fetch it, compute the hash again and validate its correctness.    

We define 3 levels of access to each row:
* public: available even to non-authenticated users (for reading)
* protected: available to all authenticated users that have `viewer` role (see below)
* private: available only to users who 'own' this data.

In addition to these 3 levels, each row defines a random 'token': everyone who knows this token can read the record.
This token should be reset any time the restriction level changes. On database level the token is set using `SET repo.token = ...`
and the table policies allow read access when the token matches.

It is assumed that the repo will host data for multiple teams; each user is a member of one or more teams.
There are few generic roles that mostly help the UI and serve as an early line of defense by annotating API methods:

* viewer: general permission to view non-public runs
* uploader: permission to upload new runs, useful for bot accounts (CI)
* tester: common user that can define tests, modify or delete data.
* admin: permission both see and change application-wide configuration such as hooks

Each team should have a role with `-team` suffix that will be the owner of the tests/runs, e.g. `engineers-team`.
Uploaders for team's data have `engineers-uploader` which is a composite role, including `engineers-team` and `uploader`.
Bot accounts that only upload data do not need read access that is represented by the `viewer` role; if the account
needs to update a run it needs this role, though (this role is not sufficient to delete anything, though; that requires the `tester` role).   
Similar to that testers should get a `engineers-tester` role which is a composite role, including `engineers-team`, `tester` and `viewer`.

     



