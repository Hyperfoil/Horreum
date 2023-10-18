---
title: Horreum Terminology
weight: 2
description: Horreum Terminology
---

Any document stored in Horreum is called a **Run** - usually this is the output of a single CI build. Horreum does not need to know much about: it accepts it as a plain JSON document and stores it in the database with very little metadata.

Each Run belongs to a series called a **Test**. This is where you can define metrics and setup regression testing.

Sometimes it's practical to execute a job in CI that produces single JSON document and upload it only once, but in fact it contains results for different configurations or several test cases. It might be more convenient to remix the Run into several documents, and this is where **Datasets** come. Datasets hold another JSON document that is created from the original Run JSON using **Transformers**, and share some metadata from the original Run. If you don't need to do anything fancy by default the Run is mapped 1:1 into a Dataset. Horreum builds on the concept of "store data now, analyze later" - therefore rather than forcing users to pre-process the Run before upload you can change the transformation and the datasets are re-created.

If you want to do anything but upload and retrieve data from Horreum, such as customize the UI or run regression testing you need to tell Horreum how to extract data from the JSON document: in case of Horreum this is a combination of jsonpaths[^1] and Javascript/Typescript code. However it's impractical to define the JSONPaths directly in the test: when you're running the test for several months or years it is quite likely that the format of your results will evolve, although the information inside stay consistent. That's why the data in both Run and Dataset should contain the `$schema` key:

```json
{
  "$schema": "urn:load-driver:1.0",
  "ci-url": "https://my-ci-instance.example.com/build/123",
  "throughput": 4567.8
}
```

For each format of your results (in other words, for each URI used in `$schema`) you can define a **Schema** in Horreum. This has several purposes:

- Validation using [JSON schema](https://json-schema.org/)
- Defines **Transformers**: Again a set of JSON paths and Javascript function that remix a Run into one or more Datasets.
- Defines a set of **Labels**: a combination of one or more JSON paths and Javascript function that extracts certain data from the document. The labels let you reuse the extraction code and process data from datasets using different schemas in a unified way.

You don't need to use all of these - e.g. it's perfectly fine to keep the JSON schema empty or use an extremely permissive one.

In our case you could create a schema 'Load Driver 1.0' using the URI `urn:load-driver:1.0`, and a Label `throughput` that would fetch jsonpath `$.throughput`. Some time later the format of your JSON changes:

```json
{
  "$schema": "urn:load-driver:2.0",
  "ci-url": "https://my-ci-instance.example.com/build/456",
  "metrics": {
    "requests": 1234,
    "duration": 10,
    "mean-latency": 12.3
  }
}
```

As the format changed you create schema 'Load Driver 2.0' with URI `urn:load-driver:2.0` and define another label in that schema, naming it again `throughput`. This time you would need to extract the data using two jsonpaths `$.metrics.requests` and `$.metrics.duration` and a function that would calculate the throughput value. In all places through Horreum you will use only the label name `throughput` to refer to the jsonpath and you can have a continuous series of results.

You can define a label `mean-latency` in Load Driver 2.0 that would not have a matching one in Load Driver 1.0. You can use that label without error even for runs that use the older schema, but naturally you won't receive any data from those.

In other cases you can start aggregating data from multiple tools, each producing the results in its own format. Each run has only single JSON document but you can merge the results into single object:

```json
{
  "load-driver": {
    "$schema": "urn:load-driver:1.0",
    "throughput": 4567.8
  },
  "monitoring": {
    "$schema": "urn:monitoring:1.0",
    "cpu-consumption": "1234s",
    "max-memory": "567MB"
  },
  "ci-url": "https://my-ci-instance.example.com/build/123"
}
```

Horreum will transparently extract the throughput relatively to the `$schema` key. Note that this is not supported deeper than on the second level as shown above, though.

[^1]: Since the jsonpath is evaluated directly in the database we use PostgreSQL [jsonpath syntax](https://www.postgresql.org/docs/12/datatype-json.html#DATATYPE-JSONPATH)
