# Security

Security uses RBAC with authz and authn provided by Keycloak server, and heavily relies on row-level security (RLS) in the database.
There should be two DB users (roles); `dbadmin` who has full access to the database, and `appuser` with limited access.
`dbadmin` should set up DB structure - tables with RLS policies and grant RW access to all tables but `dbsecret` to `appuser`.
When the application performs a database query, impersonating the authenticated user, it invokes `SET horreum.userroles = '...'`
to declare all roles the user has based on information from Keycloak. RLS policies makes sure that the user cannot read or modify
anything that does not belong to this user or is made available to him.

As a precaution against bug leaving SQL-level access open the `horreum.userroles` granting the permission are not set in plaintext;
the format of the setting is `role1:seed1:hash1,role2:seed2:hash2,...` where the `hash` is SHA-256 of combination of role, seed
and hidden passphrase. This passphrase is set in `application.properties` under key `horreum.db.secret`, and in database as the only
record in table `dbsecret`. The user `appuser` does not have access to that table, but the security-defined functions used
in table policies can fetch it, compute the hash again and validate its correctness.

We define 3 levels of access to each row:

- public: available even to non-authenticated users (for reading)
- protected: available to all authenticated users that have `viewer` role (see below)
- private: available only to users who 'own' this data.

In addition to these 3 levels, each row defines a random 'token': everyone who knows this token can read the record.
This token should be reset any time the restriction level changes. On database level the token is set using `SET horreum.token = ...`
and the table policies allow read access when the token matches.

It is assumed that the repo will host data for multiple teams; each user is a member of one or more teams.
There are few generic roles that mostly help the UI and serve as an early line of defense by annotating API methods:

- viewer: general permission to view non-public runs
- uploader: permission to upload new runs, useful for bot accounts (CI)
- tester: common user that can define tests, modify or delete data.
- admin: permission both see and change application-wide configuration such as global actions

Each team should have a role with `-team` suffix that will be the owner of the tests/runs, e.g. `engineers-team`.
Uploaders for team's data have `engineers-uploader` which is a composite role, including `engineers-team` and `uploader`.
Bot accounts that only upload data do not need read access that is represented by the `viewer` role; if the account
needs to update a run it needs this role, though (this role is not sufficient to delete anything, though; that requires the `tester` role).
Similar to that testers should get a `engineers-tester` role which is a composite role, including `engineers-team`, `tester` and `viewer`.

# SSL in `horreum-client`
You can set a certificate in runtime for `HorreumClient`
```java
HorreumClient.Builder builder = ..
builder.sslContext(certFilePath);
```
or create a custom SSLContext
```java
SSLContext ctx = ...

HorreumClient.Builder builder = ..
builder.sslContext(ctx);
```
