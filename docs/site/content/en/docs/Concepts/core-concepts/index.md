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

It allows Horreum to process the JSON content to provide validation, charting, and change detection 

A `Schema` defines the following; 

1. An *optional* expected structure of a Dataset via JSON validation schemas
2. **Required** [`Labels`](#label) that define how to use the data in the JSON document
3. *Optional* [`Transformers`](#transformers), to transform uploaded JSON documents into one or more datasets.

A Schema can apply to an entire Run JSON document, or parts of a Run JSON document

## Label
A `Label` is **required** to define how metrics are extracted from the JSON document and processed by Horreum. 

**Labels** are defined by one or more **required** [`Extractors`](#extractor) and an *optional* [`Combination Function`](#combination-function)

There are 2 types of Labels:

- `Metrics Label`: Describes a metric to be used for analysis, e.g. “Max Throughput”, “process RSS”, “startup time” etc
- `Filtering Label`: Describes a value that can be used for filtering [`Datasets`](#dataset) and ensuring that datasets are comparable, e.g. “Cluster Node Count”, “Product version” etc

A `Label` can be defined as either a `Metrics` label, a `Filtering` label, or both. 

`Filtering Labels` are combined into [`Fingerprints`](#fingerprint) that uniquely identify comparable Datasets within uploaded Runs.

## Extractor
An `Extractor` is a **required** JSONPath expression that refers to a section of an uploaded JSON document. An Extractor can return on of;

- A scalar value
- An array of values
- A subsection of the uploaded JSON document

{{% alert title="Note" %}}In the majority of cases, an Extractor will simply point to a single, scalar value{{% /alert %}}

## Combination Function:
A Combination Function is an optional Javascript function that takes all Extractor values as input and produces a Label value. 

{{% alert title="Note" %}}In the majority of cases, the Combination Function is simply an Identity function with a single input and does not need to be defined{{% /alert %}}

## Test
A `Test` is a **required** repository for particular similar [`runs`](#run) and [`datasets`](#dataset)

You can think of a `test`` as a repo for the results of a particular benchmark, i.e. a benchmark performs a certain set of actions against a system under test

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

