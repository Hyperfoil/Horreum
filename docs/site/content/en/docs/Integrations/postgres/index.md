---
title: Postgres
date: 2023-10-12
description: Postgres as the datasource to store JSON documents
categories: [Integration, Datasource]
weight: 1
---

By default, the datasource is configured to use the `postgres` database backend.

Data submitted to the Horreum API will be stored in a Postgres database, alongside all the metadata required to query and retrieve the data.

Query results from other datasources will be cached in the same `postgres` database.

## Configuration

To configure a test to use the `postgres` backend, you can:

1. Navigate to a test configuration page, e.g. `http://localhost:8080/test/10`
2. Select the `Postgres - Default` backend from the `Datastore` dropdown

{{% imgproc postgres Fit "432x541" %}}
Postgres Datastore
{{% /imgproc %}}

3. Click `Save`

## Migrating Data from Other Backends

If you wish to migrate data from one backend store to the `postgres` backend, you can configure a `Test` to retrieve the data from the old backend and submit it to the Horreum API.

Once the data has been cached, you can then use the `postgres` backend as the `Test` backend and any new data pushed to Horreum will be analyzed alongside the migrated data.