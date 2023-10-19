---
title: ElasticSearch
date: 2024-01-02
description: Use ElasticSearch to query JSON for analysis
categories: [Integration, Datasource]
weight: 1
---

If you have a lot of data already stored in an Elasticsearch instance, you can query it and analyze the data for regressions in Horreum. 

## Configuration

To configure a test to use the `elasticsearch` backend, you need to be a team administrator. With the correct permissions, you can:

1. Generate a new API key for the `elasticsearch` backend: Please see the elasticsearch docs on how to [generate an API key on the Management page](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/current/getting-started-java.html)
2. Navigate to `Administration` -> `Datastores` configuration page, e.g. `http://localhost:8080/admin#datastores`
2. Select the `Team` from the `Team` dropdown that you wish to configure
2. Click `New Datastore`
{{% imgproc new-datastore Fit "1115x469" %}}
New Datastore
{{% /imgproc %}}
3. Configure the `Elasticsearch` Datastore:
{{% imgproc modal Fit "1115x469" %}}
New Datastore
{{% /imgproc %}}
   1. Select `Elasticsearch` from the `Datastore Type` dropdown
   2. Provide a `Name` for the Datastore
   3. Enter the `URL` for the Elasticsearch instance
   4. Enter the `API Key` for the Elasticsearch instance, generated in step 1
   5. Click `Save`

## Test Configuration

To configure a test to use the `elasticsearch` backend, you can:

1. Navigate to a test configuration page, e.g. `http://localhost:8080/test/10`
2. Select the `Elasticsearch` backend defined in the `Datastores` configuration from the `Datastore` dropdown

{{% imgproc configure-test Fit "1115x469" %}}
Configure Test
{{% /imgproc %}}

3. Click `Save`
