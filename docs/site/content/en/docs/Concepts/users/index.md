---
title: Users and security
weight: 3
description: User management and security
---

It is assumed that the repo will host data for multiple teams; each user is a member of one or more teams.
Each run, test or schema is owned by one of the teams. The team corresponds to a Keycloak role (see below) with `-team` suffix, e.g. `engineers-team`. In the UI this will be displayed simply as `Engineers`, dropping the suffix and capitalizing the name.

## Data access

We define 3 levels of access to each item (test, run, dataset or schema):

- public: available even to non-authenticated users (for reading)
- protected: available to all authenticated users that have the `viewer` role (see below)
- private: available only to users who 'own' this data - those who have the team role.

In addition to these 3 levels, runs and schemas can have a 'token' (randomly generated string): everyone who knows this token can read the record. This token is reset any time the restriction level changes.

Tests can have tokens, too: you can have an arbitrary number of tokens, each with a subset of read, modify and upload privileges.

## Users and roles

Users and teams are managed in Keycloak. In non-production environment you can reach it on [localhost:8180](http://localhost:8180/) using credentials `admin`/`secret`.

There are few generic roles automatically created during initial realm import.

- viewer: general permission to view non-public runs
- uploader: permission to upload new runs, useful for bot accounts (CI)
- tester: common user that can define tests, modify or delete data.
- manager: set team members and their roles within the team
- admin: permission both see and change application-wide configuration such as global actions

Besides the team role itself (e.g. `engineers-team`) there must be a composite roles for each team combining the team role and permission role: bot account that uploads team's data will have `engineers-uploader` which is a composite role, including `engineers-team` and `uploader`. This role cannot view team's private data, it has a write-only access.
Users who explore runs, create and modify new tests should have the `engineers-tester` role; a composite role including `engineers-team`, `tester` and `viewer`.
You can also create a role that allows read-only access to team's private runs, `engineers-viewer` consisting of `engineers-team` and `viewer`.

The `admin` role is not tied to any of the teams.
                                                    

## Machine accounts

A team manager can create `Machine Accounts` in his managed team(s). Those accounts can access Horreum with HTTP Basic authentication instead of authenticating with Keycloak. This is done on the `Machine Accounts` tab on the user profile.

{{% imgproc machine_account_new Fit "1200x800" /%}}

After an account is created it is visible in the list of `Machine Accounts`. Multiple accounts can be created for a team.  

{{% imgproc machine_accounts_tab Fit "1200x800" /%}}

To change the password of an existing account click on the red `Reset Password` button and confirm. A dialogue with the new generated password appears. Copy the password and close the dialogue. This is the only time the password is visible.

{{% imgproc machine_pw_reset Fit "1200x800" /%}}
