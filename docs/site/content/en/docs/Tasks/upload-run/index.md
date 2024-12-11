---
title: Upload Run
description: 
date: 2023-10-15
weight: 3
---

Horreum accepts any valid **JSON** as the input. To get maximum out of Horreum, though, it is recommended to categorize the input using [JSON schema](https://json-schema.org/).

Operations are authorized via API Keys, for details on how to generate an API Key, please refer to [API keys](/docs/tasks/api-keys).

If you're running your tests in Jenkins you can skip a lot of the complexity below using [Horreum Plugin](https://plugins.jenkins.io/horreum/). This plugin supports both Jenkins Pipeline and Freeform jobs.

## Uploading Data

There are several mandatory parameters for the upload:

- JSON `data` itself
- `test`: Name or numeric ID of an existing test in Horreum. You can also use JSON Path to fetch the test name from the data, e.g. `$.info.benchmark`.
- `start`, `stop`: Timestamps when the run commenced and terminated. This should be epoch time in milliseconds, ISO-8601-formatted string in UTC (e.g. `2020-05-01T10:15:30.00Z`) or a JSON Path to any of above.
- `owner`: Name of the owning role with `-team` suffix, e.g. `engineers-team`.
- `access`: one of `PUBLIC`, `PROTECTED` or `PRIVATE`. See more in [data access](/docs/about/users#data-access).

Optionally you can also set `schema` with URI of the JSON schema, overriding (or providing) the `$schema` key in the `data`. You don't need to define the schema in Horreum ahead, though, the validation is triggered automatically whenever you add a Run or update the schema, and you'll see the result icon in Runs/Datasets listing for given test.

The upload itself can look like:

```bash
API_KEY='HUSR_00000000_0000_0000_0000_000000000000'
TEST='$.info.benchmark'
START='2021-08-01T10:35:22.00Z'
STOP='2021-08-01T10:40:28.00Z'
OWNER='dev-team'
ACCESS='PUBLIC'
curl 'http://localhost:8080/api/run/data?test='$TEST'&start='$START'&stop='$STOP'&owner='$OWNER'&access='$ACCESS \
    -s -X POST -H 'content-type: application/json' \
    -H "X-Horreum-API-Key: $API_KEY" \
    -d @/path/to/data.json
```

Assuming that you've [created the test](/docs/tasks/create-new-test) let's try to upload this JSON document:

```json
{
  "$schema": "urn:my-schema:1.0",
  "info": {
    "benchmark": "FooBarTest",
    "ci-url": "https://example.com/build/123"
  },
  "results": {
    "requests": 12345678,
    "duration": 300 // the test took 300 seconds
  }
}
```

When you open Horreum you will see that your tests contains single run in the 'Run Count' column.

{{% imgproc tests Fit "1200x500" %}}
Tests List
{{% /imgproc %}}


Click on the run count number with open-folder icon to see the listing of all runs for given test:

{{% imgproc runs Fit "1200x500" %}}
Runs List
{{% /imgproc %}}

Even though the uploaded JSON has `$schema` key the Schema column in the table above is empty; Horreum does not know that URI yet and can't do anything with that. You can hit the run ID with arrow icon in one of the first columns and see the contents of the run you just created:

{{% imgproc run Fit "1200x500" %}}
Run Details
{{% /imgproc %}}

This page shows the Original Run and an empty Dataset #1. The Dataset content is empty because without the Schema it cannot be used in any meaningful way - let's [create the schema and add some labels](/docs/tasks/define-schema-and-views).
