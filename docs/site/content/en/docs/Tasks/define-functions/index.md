---
title: Define Functions
description: Defining a Function in Horreum is commonly used to modify structure and generate new values
date: 2024-02-29
weight: 4
---
> **Prerequisites**: You have already

> 1. [created a Test](/docs/tasks/create-new-test/)

> 2. [uploaded](/docs/tasks/upload-run/) some data

 Using [`Functions`](/docs/concepts/core-concepts#function) in `Horreum` is a feature that provides a great deal of bespoke functionality to `Horreum` that is under the control of a user. The ability to use a [`Function`](/docs/concepts/core-concepts#function) written in JavaScript.

These Functions can be categorized as:

* Selector (filter) - used for applying conditions on input data to return output data
* Transformation - used for changing the data model
* Combination - used for computing a scalar value
* Rendering - reformatting the presentation of the data

 When using `Horreum` you will find [`Functions`](/docs/concepts/core-concepts#function) used in these Horreum objects:

| Function Type  | Horreum Object | Use                                                 |
|----------------|---------------|-----------------------------------------------------|
| Selector       | `Test`        | `Experiment`, `Timeline`, `Fingerprint`, `Variable` |
||`Report`| `Filtering`, `Category`, `Series`, `Scale Label`|
| Transformation | `Schema`      | `Transformer`|
||`Report`| `Components`|
| Combination    | `Schema`      | `Label`, `Transformer`|
| Rendering      | `Test`        | `View`|
||`Report`| `Category`, `Series`, `Scale`|

## Making use of Horreum Functions

 JavaScript [ECMAScript 2023](https://262.ecma-international.org/14.0/) specification is available throughout Horreum [`Functions`](/docs/concepts/core-concepts#function).

## Example Filtering Function

These [`Functions`](/docs/concepts/core-concepts#function) rely on a condition evaluation to return a **boolean** value.
The following will filter based on the individual [`Label`](/docs/concepts/core-concepts#label) [`Extractor`](/docs/concepts/core-concepts#extractor) only having the value 72.

```javascript
value => value === 72
```

## Example Transformation Functions

Transformation [`Functions`](/docs/concepts/core-concepts#function) rely on a returned value that is an **Object**, **Array** or **scalar** value.
This Transformation [`Function`](/docs/concepts/core-concepts#function) relies on 12 [`Extractors`](/docs/concepts/core-concepts#extractor) setup on the [`Schema`](/docs/concepts/core-concepts#schema) [`Label`](/docs/concepts/core-concepts#label). Each [`Extractor`](/docs/concepts/core-concepts#extractor) configured to obtain an **Array** of data items (except `buildId` and `buildUrl`).

Input JSON
```json
{
  "runtimeName": "spring-native",
  "rssStartup": 55,
  "maxRss": 15,
  "avBuildTime": 1234,
  "avTimeToFirstRequest": 5,
  "avThroughput": 25,
  "rssFirstRequest": 5000,
  "maxThroughputDensity": 15,
  "buildId": "x512",
  "buildUrl": "http://acme.com",
  "quarkusVersion": "0.1",
  "springVersion": "3.0"
}
```
This Transformation [`Function`](/docs/concepts/core-concepts#function) uses the `map` JavaScript function to modify property names, the number of JSON properties and values. In the transformation `runtime` and `buildType` are created from the filtered `runtimeName` property. The `version` property is conditionally derived from `runtimeName` depending on the presence of the text **spring**.
```javascript
({runtimeName, rssStartup, maxRss, avBuildTime, avTimeToFirstRequest, avThroughput, rssFirstRequest, maxThroughputDensity, buildId, buildUrl, quarkusVersion, springVersion}) => {
    var map = runtimeName.map((name, i) => ({
        runtime: name.split('-')[0],
        buildType: name.split('-')[1],
        rssStartup: rssStartup[i],
        maxRss: maxRss[i],
        avBuildTime: avBuildTime[i],
        avTimeToFirstRequest: avTimeToFirstRequest[i],
        avThroughput: avThroughput[i],
        rssFirstRequest: rssFirstRequest[i],
        maxThroughputDensity: maxThroughputDensity[i],
        buildId: buildId,
        buildUrl: buildUrl,
        version: ((name.split('-')[0].substring(0, 6) == 'spring' ) ? springVersion: quarkusVersion )
    }))
    return map;
}
```
Output JSON
```json
{
  "runtime": "spring",
  "buildType": "native",
  "rssStartup": 55,
  "maxRss": 15,
  "avBuildTime": 1234,
  "avTimeToFirstRequest": 5,
  "avThroughput": 25,
  "rssFirstRequest": 5000,
  "maxThroughputDensity": 15,
  "buildId": "x512",
  "buildUrl": "http://acme.com",
  "version": "3.0"
}
```

## Example Combination Functions

Combination [`Functions`](/docs/concepts/core-concepts#function) rely on a returned value that is an **Object**, **Array** or **scalar** value.

Input JSON
```json
[5,10,15,20,10]
```
This [`Function`](/docs/concepts/core-concepts#function) will conditionally reduce an array of values unless there is only a single value of type number.
```javascript
value => typeof value === "number" ? value : value.reduce((a, b) => Math.max(a, b))
```
Output JSON
```json
20
```

The following example returns a scalar **Float** value.

Input JSON
```json
{
  "duration": "62.5",
  "requests": "50"
}
```
This [`Function`](/docs/concepts/core-concepts#function) will create a value of the amount of time per request with the exponent rounded to 2 figures.
```javascript
value => (value.duration / value.requests).toFixed(2)
```
Output JSON
```json
1.25
```

## Example Rendering Functions

A Rendering [`Function`](/docs/concepts/core-concepts#function) will change the presentation or add metadata for rendering in the UI.

Input JSON
```json
Hello World
```
This Rendering [`Function`](/docs/concepts/core-concepts#function) adds HTML markup and sets the color of the span text.
```javascript
value => '<span style="color: Tomato";>' + value + '</span>'
```
Output text
```html
<span style="color: Tomato;">Hello World</span>
```

## Troubleshooting Functions.

See the section dedicated to [Troubleshooting](/docs/reference/troubleshooting-functions/) [`Functions`](/docs/concepts/core-concepts#function).
