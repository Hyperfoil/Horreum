---
title: Troubleshooting Functions
description: Steps to troubleshoot use of Functions in Horreum
date: 2024-02-27
weight: 4
---

## Troubleshooting Functions and Combination Function

Creating `Scheam Labels` is one of the key capabilities that enables working with loosely defined data. Using a [`JSON Validation Schema`](/docs/concepts/core-concepts#json-validation-schema) provides useful guarentees data in an uploaded [`Run`](/docs/concepts/core-concepts#run) JSON conforms to expected data types. A [`Validation Schema`](/docs/concepts/core-concepts#json-validation-schema) is optional but when not used makes the handling of metrics unreliable as data types may change.  

To troubleshoot use of JavaScript [`Function`](/docs/concepts/core-concepts#function) the following techniques can be used

* The `typeof` function to discover the data-type of the [`Extractor`](/docs/concepts/core-concepts#extractor) variable, **Object** property or **Array** element in the [`Label`](/docs/concepts/core-concepts#label) [`Combination Function`](/docs/concepts/core-concepts#combination-function), using the *Test label calculation* button to reveal the JavaScript data-type
{{% imgproc use-typeof-function Fit "785x769" %}}
Test label calculation
{{% /imgproc %}}
* View the `Labels calculation Log` window and display **INFO** or **DEBUG** log level, this reveals syntax errors when the function is executed
{{% imgproc labels-calculation-log Fit "800x257" %}}
Logged in user, Labels Calculation Log
{{% /imgproc %}}
* Use the *Show label values* button, provides a quick visual check of resulting values for the [`Run`](/docs/concepts/core-concepts#run) [`Dataset`](/docs/concepts/core-concepts#dataset)
{{% imgproc buttons-on-dataset-view Fit "1091x462 " %}}
Run Dataset view
{{% /imgproc %}}
`Effective label values` window reveals the anticipated value or error text which can be due to the following:
- Nan - Numerical expression involves a data-type that is incompatible 
- (undefined) - Incorrect JSONPath field value used in the [`Label`](/docs/concepts/core-concepts#label) [`Extractor`](/docs/concepts/core-concepts#extractor)
- empty - Absence of any value in the uploaded [`Run`](/docs/concepts/core-concepts#run)
{{% imgproc effective-label-value Fit "700x351" %}}
Effective label values
{{% /imgproc %}}

