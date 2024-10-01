---
title: Collector API
date: 2024-10-01
description: Use Collector API to query JSON for analysis
categories: [Integration, Datasource]
weight: 1
---

If you have a lot of data already stored in a [Collector](https://github.com/Karm/collector) instance, you can query it and analyze the data for regressions in Horreum.

## Configuration

To configure a test to use the `Collector API` backend, you need to be a team administrator. With the correct permissions, you can:

1. Generate a new API key for the `Collector API` backend: Please see the collector docs on how to [Create a new API token](https://github.com/Karm/collector?tab=readme-ov-file#create-a-new-api-token)
2. Navigate to `Administration` -> `Datastores` configuration page, e.g. `http://localhost:8080/admin#datastores`
2. Select the `Team` from the `Team` dropdown that you wish to configure
2. Click `New Datastore`
   {{% imgproc new-datastore Fit "1115x469" %}}
   New Datastore
   {{% /imgproc %}}
3. Configure the `Collector API` Datastore:
   {{% imgproc modal Fit "1115x469" %}}
   New Collector API Datastore
   {{% /imgproc %}}
    1. Select `Collector API` from the `Datastore Type` dropdown
    2. Provide a `Name` for the Datastore
    3. Enter the `URL` for the Collector instance
    4. Enter the `API Key` for the Collector instance, generated in step 1
    5. Click `Save`

## Test Configuration

To configure a test to use the `Collector API` backend, you can:

1. Navigate to a test configuration page, e.g. `http://localhost:8080/test/10`
2. Select the `Collector API` backend defined in the `Datastores` configuration from the `Datastore` dropdown

{{% imgproc configure-test Fit "1115x469" %}}
Configure Test
{{% /imgproc %}}

3. Click `Save`
