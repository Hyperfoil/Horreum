---
title: Core Concepts
weight: 1
description: Core Horreum concepts
---


## Teams
A `Team` is a **required** top-level organizational and authorization construct.

## Folders
A `Folder` is an *optional* organizational structure to hold [`Tests`](#test)

## Schema

A `Schema` is **required** by Horreum to define the meta-data associated with a [`Run`](#run)

It allows `Horreum` to process the JSON content to provide validation, charting, and change detection 

A `Schema` defines the following; 

1. An *optional* expected structure of a Dataset via a [`JSON Validation Schema`](#json-validation-schema)
2. **Required** [`Labels`](#label) that define how to use the data in the JSON document
3. *Optional* [`Transformers`](#transformers), to transform uploaded JSON documents into one or more datasets.

A `Schema` can apply to an entire Run JSON document, or parts of a Run JSON document

## Label
A `Label` is **required** to define how metrics are extracted from the JSON document and processed by Horreum. 

**Labels** are defined by one or more **required** [`Extractors`](#extractor) and an *optional* [`Combination Function`](#combination-function)

There are 2 types of Labels:

- `Metrics Label`: Describes a metric to be used for analysis, e.g. “Max Throughput”, “process RSS”, “startup time” etc
- `Filtering Label`: Describes a value that can be used for filtering [`Datasets`](#dataset) and ensuring that datasets are comparable, e.g. “Cluster Node Count”, “Product version” etc

A `Label` can be defined as either a `Metrics` label, a `Filtering` label, or both. 

`Filtering Labels` are combined into [`Fingerprints`](#fingerprint) that uniquely identify comparable Datasets within uploaded Runs.

## Extractor
An `Extractor` is a **required** JSONPath expression that refers to a section of an uploaded JSON document. An Extractor can return one of:

- A scalar value
- An array of values
- A subsection of the uploaded JSON document

{{% alert title="Note" %}}In the majority of cases, an Extractor will simply point to a single, scalar value{{% /alert %}}

## Combination Function:
A Combination Function is an optional JavaScript function that takes all Extractor values as input and produces a Label value. See [Function](#function)

{{% alert title="Note" %}}In the majority of cases, the Combination Function is simply an Identity function with a single input and does not need to be defined{{% /alert %}}

## Test
A `Test` is a **required** repository for particular similar [`runs`](#run) and [`datasets`](#dataset)

You can think of a `test` as a repo for the results of a particular benchmark, i.e. a benchmark performs a certain set of actions against a system under test

`Test` [`Runs`](#run) can have different configurations, making them not always directly comparable, but the Run results stored under one Test can be filtered by their [`Fingerprint`](#fingerprint) to ensure that all [`Datasets`](#dataset) used for analysis are comparable

## Run
A `Run` is a particular single upload instance of a [`Test`](#test) 

A `Run` is associated with one or more [`Schemas`](#schema) in order to define what data to expect, and how to process the JSON document

## Transformers
A `Transformer` is *optionally* defined on a [`Schema`](#schema) that applies **required** [`Extractors`](#extractor) and a **required** [`Combination Function`](#combination-function) to transform a [`Run`](#run) into one or more [`Datasets`](#dataset)

`Transformers` are typically used to;

- Restructure the JSON document. This is useful where users are processing JSON documents that they do not have control of the structure, and the structure is not well defined
- Split a [`Run`](#run) JSON output into multiple, non-comparable [`Datasets`](#dataset). This is useful where a benchmark iterates over a configuration and produces a JSON output that contains multiple results for different configurations

A [`Schema`](#schema) can have 0, 1 or multiple `Transformers` defined

{{% alert title="Note" %}}In the majority of cases, the [`Run`](#run) data does not need to be transformed and there is a one-to-one direct mapping between [`Run`](#run) and [`Dataset`](#dataset).  In this instance, an `Identity Transformer` is used and does not need to be defined by the user{{% /alert %}}

## Dataset
A `Dataset` is either the entire [`Run`](#run) JSON document, or a subset that has been produced by an *optional* [`Transformer`](#transformers)

It is possible for a [`Run`](#run) to include multiple [`Datasets`](#dataset), and the [`Transformer(s)`](#transformers) defined on a [`Schema`](#schema) associated with the [`Run`](#run) has the job of parsing out the multiple [`Datasets`](#dataset)

{{% alert title="Note" %}}In most cases, there is a 1:1 relationship between a [`Run`](#run) and a [`Dataset`](#dataset), when the [`Dataset`](#dataset) is expected to have one unified set of results to be analyzed together{{% /alert %}}

## Fingerprint

A `Fingerprint` is combination of [`Filtering labels`](#label) that unique identifies comparable [`datasets`](#dataset) within a [`test`](#test)

## Function
A `Function` is a JavaScript function that is executed on the server side. Functions can be used for validating expected data formats, substitution and rendering. Also used in [`Combination Function`](#combination-function)s to create derived metrics. See [Define Functions](/docs/tasks/define-functions/) for more detailed information.

## Datasource

A `Datasource` is a **required** top-level organizational construct that defines the source of the data to be stored or retrieved by Horreum. Currently, Horreum supports 2 types of `Datasource`: Postgres and Elasticsearch

## Baseline

The *initial* sample for an [`Experiment`](#experiment) comparison. Configured in an [`Experiment Profile`](#experiment-profile).

## Change Detection Variable

Change detection tracks [`Schema`](#schema) [`Label`](#label)s that have been configured as a Change Detection Variable.

## Experiment

This enables running a comparison between [`Runs`](#run) for a particular [`Test`](#test). There can be multiple [`Profile`](#experiment-profile)s configured for an [`Experiment`](#experiment) to check for a variety of desired conditions. The outcome status for each [`Profile`](#experiment-profile) condition will be one of the following:
- SAME
- WORSE
- BETTER

## Experiment Profile

A [`Profile`](#experiment-profile) consists of:
- [`Experiment`](#experiment) selector
- [`Baseline`](#baseline)
- 0, 1 or many Comparison conditions

## JSON validation schema
An *optional* schema added to a [`Test`](#test) to validate uploaded [`Run`](#run) JSON data.

## Report Configuration
In Horreum a `Report Configuration` is used for compiling a summary of information to be displayed using tables.

## Report
A `Report` is an instance of a `Report Configuration`. Creation date and time is used for differentiating each `Report`.

## Actions
An `Action` is the ability by Horreum to send an Event notification to an external system. These are added to a [`Test`](#test).

## Global Action
Actions that occur globally in `Horreum`

## Test Action
Actions only for a particular [`Test`](#test)

## Action allow list
`Horreum` will only allow generic HTTP requests to domains that have been pre-configured in Horreum by an Administrator.
