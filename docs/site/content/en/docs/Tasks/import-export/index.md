---
title: Import and Export Tests and Schemas
date: 2025-03-26
description: How to import and export Tests and Schemas in Horreum
categories: [Tutorial]
weight: 3
---

## Background

To simplify copying [Tests](https://horreum.hyperfoil.io/docs/concepts/core-concepts/#test) and [Schemas](https://horreum.hyperfoil.io/docs/concepts/core-concepts/#schema) between Horreum instances Horreum provides a simple API to export and import new Tests and Schemas. Horreum also support updating exising Schemas and Tests by importing Tests or Schemas with existing Id's.
For security reasons you need to be part of the team or an admin to be able to import/export Tests/Schemas.

> **Prerequisites**:
> 1. Horreum is running
> 2. To export you have previously defined a `Schema` for the JSON data you wish to analyze, please see [Define a Schema](/docs/tasks/define-schema-and-views/)
> 3. To export you have previously defined a Test, please see [Create new Test](/docs/tasks/create-new-test/)

## Import or Export using the UI

This section explains how to import and export tests or schemas using the Horreum UI.

### Export Tests or Schemas

To export a test or schema, navigate to the corresponding entity in the UI and select the "**Export**" tab.

> Note: You must be logged in to access the "**Export**" tab.

{{% imgproc export_test Fit "1200x300" %}}
Export a test
{{% /imgproc %}}

The following are simple examples of an exported Test

```json
{
  "variables": [],
  "missingDataRules": [],
  "experiments": [],
  "actions": [],
  "subscriptions": {
    "users": [],
    "optout": [],
    "teams": [],
    "testId": 109
  },
  "id": 123,
  "name": "ScaleTest",
  "description": "My awesome description",
  "datastoreId": null,
  "fingerprintLabels": [],
  "transformers": [],
  "notificationsEnabled": true,
  "access": "PUBLIC",
  "owner": "dev-team"
}
```

and an exported Schema
```json
{
  "labels": [
    {
      "id": 3210,
      "name": "value",
      "extractors": [
        {
          "name": "value",
          "jsonpath": "$.value",
          "isarray": false
        }
      ],
      "filtering": true,
      "metrics": true,
      "schemaId": 322,
      "access": "PUBLIC",
      "owner": "dev-team"
    }
  ],
  "transformers": [],
  "id": 321,
  "uri": "urn:dummy:1.0",
  "name": "Dummy Schema",
  "description": "This schema is here just to test some functionality in production...",
  "access": "PRIVATE",
  "owner": "dev-team"
}
```

### Import Tests or Schemas

To import a test or schema, click the "**Import Test**" button on either the "**Tests**" or "**Schemas**" page. 
The following screenshot shows the button on the "**Tests**" page.

Clicking the button opens a popup where you can either drag and drop your file or browse your directory to select it.

{{% imgproc import_test Fit "1700x800" %}}
Import a test
{{% /imgproc %}}

Be aware that this functionality is used for both creating a new entity or updating an existing one, therefore
that's your responsibility to provide the correct JSON in according to what is your purpose. In order to trigger 
either an update or a creation, it is all about the top entity's ID: if present the process will treat it as an 
update otherwise as a creation.

>Important: Importing can either create a new entity or update an existing one. The process depends on the **ID** in the JSON:
>  * If the ID is present, the entity is updated.
>  * If the ID is missing, a new entity is created.

Make sure to provide the correct JSON format based on your intent.

## Duplicate a Test or Schema

Exploiting this export/import functionalities you can easily duplicate Tests in this way:

1. Export the test you want to duplicate
2. Update the exported JSON by removing the top **ID** key (or set to `null`)
3. Update the name of the Test, otherwise it will collide with the one you are duplicating
4. Import the new JSON using the "**Import test**" functionality

> Note: when importing a new Test, you don't have to care about cleaning up all the IDs of the sub-entities.
> The process itself will care about that, so that new entities will be created.

## Import or Export using the API

You can do the same using the exposed Horreum API. The only prerequisite is having a valid **API Key**, 
if you are unfamiliar with generating an API Key, please see [Upload Run](/docs/tasks/api-keys/).

### Export Schemas

```bash
API_KEY=HUSR_00000000_0000_0000_0000_000000000000
SCHEMAID='123'
curl "http://localhost:8080/api/schema/$SCHEMAID/export" -H "X-Horreum-API-Key: $API_KEY"
```

### Export Tests

```bash
API_KEY=HUSR_00000000_0000_0000_0000_000000000000
TESTID='123'
curl "http://localhost:8080/api/test/$TESTID/export" -H "X-Horreum-API-Key: $API_KEY"
```

### Import Schemas

```bash
curl -X POST 'http://localhost:8080/api/schema/import' \
  -H "X-Horreum-API-Key: $API_KEY" \
  -H 'content-type: application/json' \
  -d @/path/to/schema.json
```

> Note: if you want to update an existing Test use `PUT` instead of `POST`

### Import Tests

```bash
curl -X POST 'http://localhost:8080/api/test/import' \
  -H "X-Horreum-API-Key: $API_KEY" \
  -H 'content-type: application/json' \
  -d @/path/to/test.json
```

> Note: if you want to update an existing Test use `PUT` instead of `POST`

## Export objects

### TestExport

The export object for Tests is called [TestExport](https://horreum.hyperfoil.io/openapi/#tag/Test/operation/importTest) and contains a lot of other fields in addition to what's defined 
in [Test](https://horreum.hyperfoil.io/docs/concepts/core-concepts/#test). This includes, variables, experiments, actions, subscriptions, datastore and missingDataRules. 
This is to simplify the import/export experience and make sure that all the data related to a Test has a single 
entrypoint in regard to import and export. Note that secrets defined on [Action](https://horreum.hyperfoil.io/docs/tasks/configure-actions/) are not portable between 
Horreum instances and there might be security concerns so they are omitted. The apiKey and password attributes defined 
on the config field in [Datastore](https://horreum.hyperfoil.io/docs/integrations/) are also omitted and will have to be manually added in a separate step.

### SchemaExport

The export object for Schemas is called [SchemaExport](https://horreum.hyperfoil.io/openapi/#tag/Schema/operation/importSchema) and contains other fields in addition to what's defined in 
[Schema](https://horreum.hyperfoil.io/docs/concepts/core-concepts/#schema). This includes, labels, extractors and transformers. This is to simplify the import/export experience and 
make sure that all the data related to a Schema has a single entrypoint in regard to import and export.
