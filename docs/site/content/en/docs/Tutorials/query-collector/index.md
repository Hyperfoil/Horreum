---
title: Query Collector API 
date: 2024-10-01
description: Query JSON data from Collector and analyze it with Horreum
categories: [Tutorial]
weight: 3
---

> **Prerequisites**:

> 1. Horreum is running, and you are logged in

> 2. You have access to a running [Collector](https://github.com/Karm/collector) instance that already contains JSON data

> 3. You have previously defined a `Schema` for the JSON data you wish to analyze, please see [Define a Schema](/docs/tasks/define-schema-and-views/)

## Create a Test and query data from a Collector instance

This tutorial will guide you through how to connect to a remote [Collector](https://github.com/Karm/collector) instance, and perform change detection on existing data in an index.

## Configure Collector Datastore

Please follow the [Collector Integration](/docs/integrations/collector/) guide to configure a new Collector Datastore. 

## Query Data from Collector

The procedure is the same as described in the [Upload your first Run](/docs/tutorials/create-test-run/) tutorial

To query data from a Collector instance, you need to know the `tag` and `imgName` of the data you wish to analyze.
You will also need to determine the date range of the data you wish to analyze using `newerThan` and `olderThan`.

```json
{
  "tag": "quarkus-main-ci",
  "imgName": "quarkus-integration-test-main-999-SNAPSHOT-runner",
  "newerThan": "2024-09-20 00:00:00.000",
  "olderThan": "2024-09-25 00:00:00.000"
}
```

where;

- **tag**: the tag of the data you wish to analyze
- **imgName**: the image name (aka test) of the data you wish to analyze
- **newerThan**: the start date of the data you wish to analyze
- **olderThan**: the end date of the data you wish to analyze

The query can be executed by making a call to the Horreum API;

```bash
$ curl 'http://localhost:8080/api/run/data?test='$TEST'&start='$START'&stop='$STOP'&owner='$OWNER'&access='$ACCESS \
    -s -H 'content-type: application/json'  -H "X-Horreum-API-Key: $API_KEY" \
    -d @/tmp/collector_query.json
```

The query will return a list of `RunID`'s for each json object retrieved and analyzed from Collector.

## What Next?

After successfully querying data from Collector, you can now:
- optionally [Transform Runs to Datasets](/docs/tasks/trasnform-runs-to-datasets/) to transform the data into datasets
- [Configure Change Detection](/docs/tasks/configure-change-detection/) to detect regressions in the data.
- [Configure Actions](/docs/tasks/configure-actions/) to trigger events when regressions are detected.