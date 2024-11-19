---
title: Import and Export Tests and Schemas
date: 2023-11-30
description: How to import and export Tests and Schemas in Horreum
categories: [Tutorial]
weight: 3
---

> **Prerequisites**:

> 1. Horreum is running
> 2. To export you have previously defined a `Schema` for the JSON data you wish to analyze, please see [Define a Schema](/docs/tasks/define-schema-and-views/)
> 3. To export you have previously defined a Test, please see [Create new Test](/docs/tasks/create-new-test/)

## Background

To simplify copying [Tests](https://horreum.hyperfoil.io/docs/concepts/core-concepts/#test) and [Schemas](https://horreum.hyperfoil.io/docs/concepts/core-concepts/#schema) between Horreum instances Horreum provides a simple API to export and import new Tests and Schemas. Horreum also support updating exising Schemas and Tests by importing Tests or Schemas with existing Id's.
For security reasons you need to be part of the team or an admin to be able to import/export Tests/Schemas.

## TestExport

The export object for Tests is called [TestExport](https://horreum.hyperfoil.io/openapi/#tag/Test/operation/importTest) and contains a lot of other fields in addition to what's defined in [Test](https://horreum.hyperfoil.io/docs/concepts/core-concepts/#test). This includes, variables, experiments, actions, subscriptions, datastore and missingDataRules. This is to simplify the import/export experience and make sure that all the data related to a Test has a single entrypoint with regards to import and export. Note that secrets defined on [Action](https://horreum.hyperfoil.io/docs/tasks/configure-actions/) are not portable between Horreum instances and there might be security concerns so they are omitted. The apiKey and password attributs defined on the config field in [Datastore](https://horreum.hyperfoil.io/docs/integrations/) are also omitted and will have to be manually added in a separate step.

## TestSchema

The export object for Schemas is called [SchemaExport](https://horreum.hyperfoil.io/openapi/#tag/Schema/operation/importSchema) and contains other fields in addition to what's defined in [Schema](https://horreum.hyperfoil.io/docs/concepts/core-concepts/#schema). This includes, labels, extractors and transformers. This is to simplify the import/export experience and make sure that all the data related to a Schema has a single entrypoint with regards to import and export.

## Import/Export using the UI

### Export or Import as an update to an existing Test/Schema

Select the Test/Schema, select the Export link and you will be given the option to import/export as seen here:

{{% imgproc import-export Fit "1200x300" %}}
import-export
{{% /imgproc %}}

### Import a new Test/Schema

Click on Schema/Test and there is a button where you can select either _Import Schema_ or _Import Test_. Select and upload file.

## Import Schemas

```bash
curl 'http://localhost:8080/api/schema/import/' \
    -s -X POST -H 'content-type: application/json' \
    -H 'X-Horreum-API-Key: '$API_KEY \
    -d @/path/to/schema.json
```

If you are unfamiliar with generating an API Key, please see [Upload Run](/docs/tasks/api-keys/).

## Import Tests

```bash
curl 'http://localhost:8080/api/test/import/' \
    -s -X POST -H 'content-type: application/json' \
    -H 'X-Horreum-API-Key: '$API_KEY \
    -d @/path/to/test.json
```

## Export Schemas

```bash
SCHEMAID='123'
curl 'http://localhost:8080/api/schema/export/?id='$SCHEMAID \
    -H 'X-Horreum-API-Key: '$API_KEY \
    -O --output-dir /path/to/folder
```

## Export Tests

```bash
TESTID='123'
curl 'http://localhost:8080/api/test/export/?id=$TESTID' \
    -s -X POST -H 'content-type: application/json' \
    -H 'X-Horreum-API-Key: '$API_KEY \
    -O --output-dir /path/to/folder
```
