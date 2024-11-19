---
title: Query Elasticsearch 
date: 2023-11-30
description: Query JSON data from Elasticsearch and analyze it with Horreum
categories: [Tutorial]
weight: 3
---

> **Prerequisites**: 

> 1. Horreum is running, and you are logged in

> 2. You have access to a running Elasticsearch instance that already contains JSON data

> 3. You have previously defined a `Schema` for the JSON data you wish to analyze, please see [Define a Schema](/docs/tasks/define-schema-and-views/) 

## Create a Test and query data from an Elasticsearch instance

This tutorial will guide you through how to connect to a remote Elasticsearch instance, and perform change detection on existing data in an index.

## Configure Elasticsearch Datastore

Please follow the [Elasticsearch Integration](/docs/integrations/elasticsearch/) guide to configure a new Elasticsearch Datastore. 

## Query Data from Elasticsearch

There are two methods for querying existing data from an Elasticsearch instance, either a single document, or multiple documents from a query.

The procedure is the same as described in the [Upload your first Run](/docs/tutorials/create-test-run/) tutorial

### Query single document from Elasticsearch datastore

To analyze a single document from Elasticsearch, you need to know the index and document id of the document you wish to analyze.

The `json` payload for a single Elasticsearch document is as follows:

```json
{
  "index": ".ds-kibana_sample_data_logs-2024.01.11-000001",
  "type": "doc",
  "query": "RUO1-IwBIG0DwQQtm-ea"
}
```

where;

- **index**: name of the Elasticsearch index storing the document
- **type**: "DOC" for a single document
- **query**: the document id of the document to analyze

The document query can then be sumitted to the Horreum API;

```bash 
$ curl 'http://localhost:8080/api/run/data?test='$TEST'&start='$START'&stop='$STOP'&owner='$OWNER'&access='$ACCESS \
    -s -H 'content-type: application/json' -H 'X-Horreum-API-Key: '$API_KEY \
    -d @/tmp/elastic_payload.json
```

The api will return the `RunID` for the document retrieved and analyzed from Elasticsearch. 

### Query Multiple documents from single index in Elasticsearch datastore

It is also possible to query multiple documents from Elasticsearch with a single call to the Horreum API.

```json
{ 
    "index": ".ds-kibana_sample_data_logs-2023.12.13-000001",
    "type": "search",
    "query": {
        "from" : 0, "size" : 100,
        "query": {
            "bool" : {
            "must" : [
                { "term" : {  "host": "artifacts.elastic.co" } }
            ],
            "filter": {
                "range": {
                    "utc_time": {
                    "gte": "2023-12-01T09:28:48+00:00",
                    "lte": "2023-12-14T09:28:48+00:00"
                    }
                }
            },
            "boost" : 1.0
            }
        }
    }

}
```

where;

- **index**: name of the Elasticsearch index storing the documents
- **type**: "SEARCH" for a query
- **query**: the Elasticsearch query to execute

> NOTE: The `query` field can be any query that Elasticsearch search API supports, please see [The Search API](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-your-data.html) for more information. 

The query can be executed by making a call to the Horreum API;

```bash
$ curl 'http://localhost:8080/api/run/data?test='$TEST'&start='$START'&stop='$STOP'&owner='$OWNER'&access='$ACCESS \
    -s -H 'content-type: application/json' -H 'X-Horreum-API-Key: '$API_KEY \
    -d @/tmp/elastic_query.json
```

The query will return a list of `RunID`'s for each document retrieved and analyzed from Elasticsearch.

### Query Multiple Index for documents in Elasticsearch datastore

If your ElasticSearch instance contains meta-data and the associated documents in separate indexes, it is possible to query the meta-data index to retrive a list of documents to analyse with Horreum using a "MULTI_INDEX" query

```json
{ 
    "index": ".ds-elastic_meta-data-index",
    "type": "multi-index",
    "query": {
      "targetIndex": ".ds-elastic_secondary-index",
      "docField": "remoteDocField",
      "metaQuery": {
        "from": 0,
        "size": 100,
        "query": {
          "bool": {
            "must": [
              {
                "term": {
                  "host": "artifacts.elastic.co"
                }
              }
            ],
            "filter": {
              "range": {
                "utc_time": {
                  "gte": "2023-12-01T09:28:48+00:00",
                  "lte": "2023-12-14T09:28:48+00:00"
                }
              }
            },
            "boost": 1.0
          }
        }
      }
    }
}
```

where;

- **index**: name of the Elasticsearch index storing the **meta-data**
- **type**: "mult-index" for a multi-index query
- **query**: 
  - **targetIndex**: the name of the index containing the documents to analyze
  - **docField**: the field in the meta-data index that contains the document id of the document to analyze
  - **metaQuery**: the Elasticsearch query to execute on the meta-data index

Horreum will query the **meta-data** index, retrieve all matching documents. The meta-data and document contents can be used in any Horreum analysis. 

The query will return a list of `RunID`'s for each document retrieved and analyzed from Elasticsearch.

## What Next?

After successfully querying data from Elasticsearch, you can now:
- optionally [Transform Runs to Datasets](/docs/tasks/trasnform-runs-to-datasets/) to transform the data into datasets
- [Configure Change Detection](/docs/tasks/configure-change-detection/) to detect regressions in the data.
- [Configure Actions](/docs/tasks/configure-actions/) to trigger events when regressions are detected.