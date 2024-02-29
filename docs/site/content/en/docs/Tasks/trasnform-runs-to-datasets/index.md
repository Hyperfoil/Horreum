---
title: Transform Runs to Datasets
description: 
date: 2023-10-15
weight: 5
---

Horreum stores data in the JSON format. The raw document uploaded to repository turns into a **Run**, however most of operations and visualizations work on **Datasets**. By default there's a 1-on-1 relation between Runs and Datasets; the default transformation extracts objects annotated with a [JSON schema](https://json-schema.org/) (the `$schema` property) and puts them into an array - it's easier to process Datasets internally after that. It is possible to customize this transformation, though, and most importantly - you can create multiple Datasets out of a single Run. This is useful e.g. when your tooling produces single document that contains results for multiple tests, or with different configurations. With the Run split into more Datasets it's much easier to display and analyze these results individually.

We assume that you've already [created a test](/docs/tasks/create-new-test), [uploaded](/docs/tasks/upload-run) some data and [defined the Schema](docs/tasks/define-schema-and-views).
In this example we use test `acme-regression` with the basic schema `urn:acme-schema:1.0` and uploaded JSON:

```json
{
  "$schema": "urn:acme-schema:1.0",
  "testName": ["Test CPU1", "Test CPU2", "Test CPU3"],
  "throughput": [0.1, 0.2, 0.3]
}
```

## Defining a Transformer

Here we will show how to define the transformation from the raw input into individual Datasets so that each _testName_ and _throughput_ goes to a separate set.

As the structure of the documents for individual tests (stored in Dataset) differs from the input document structure (`urn:acme-schema:1.0`) we will define a second Schema - let's use the URI `urn:acme-sub-schema:1.0`.

Back in the `acme-schema` we switch to _Transformers_ tab and add a new `CpuDatasetTransformer`. In this **Transformer** we select the `acme-sub-schema` as **Target schema URI**: the `$schema` property will be added to each object produced by this Transformer. An alternative would be setting the target schema manually in the **Combination function** below. Note that it is vital that each Transformer annotates its output with some schema - without that Horreum could not determine the structure of data and process it further.

We add two extractors: _testName_ and \_throughput_that will get the values from the raw JSON object. These values are further processed in the **Combination function**. If the function is not defined the result will be an object with properties matching the extractor names - the same object as is the input of this function.

As a result, the transformer will return an array of objects where each element contributes to a different DataSet.

{{% imgproc transformer_setup Fit "1200x900" %}}
Transformer Setup
{{% /imgproc %}}


## Use transformers in the test

Each schema can define multiple Transformers, therefore we have to assign our new transformer to the `acme-regression` test.

Tests > Transformers

{{% imgproc test_transformers Fit "1200x900" %}}
Test Transformers
{{% /imgproc %}}

After _Saving_ the test press _Recalculate datasets_ and then go to _Dataset list_ to see the results.
You will find 3 Datasets, each having a separate test result.

{{% imgproc datasets Fit "1200x900" %}}
Datasets
{{% /imgproc %}}

## Use labels for the fingerprint

When you've split the Run into individual Datasets it's likely that for purposes of Change Detection you want to track values from each test individually. Horreum can identify such independent series through a **Fingerprint**: set of labels with an unique combination of values.

Go to the `acme-sub-schema` and define the labels _testname_ and _throughput_: the former will be used for the Fingerprint, the latter will be consumed in a Change Detection Variable.

{{% imgproc labels Fit "1200x900" %}}
Labels
{{% /imgproc %}}

Then switch to Test > Change detection and use thos labels. The **Fingerprint filter** is not necessary here (it would let you exclude some Datasets from Change detection analysis.

{{% imgproc variables Fit "1200x900" %}}
Variables
{{% /imgproc %}}

After saving and recalculation you will see the new data-points in **Changes**

{{% imgproc change Fit "1200x900" %}}
Change
{{% /imgproc %}}

In this guide we transformed the **Run** from the batch results arrays to individual **Datasets**.
Then we extracted data using **Labels** and them for Change detection.
