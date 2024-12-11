---
title: API Filter Queries
description: Reference guide for API Filter Query
date: 2024-03-27
weight: 3
---

Some APIs provide the ability to filter results when HTTP API response content type is `application-json`. The `API Filter Query` operates on the server side to reduce payload size.

API operations that support Filter Query provide an HTTP request parameter named *query*. The value of the *query* parameter is a `JSONPath` expression. Any syntax errors in the `JSONPath` will cause the operation to fail with details of the  syntax issue.

To use the Filter Query with an operation that returns a [Run](/docs/concepts/core-concepts/#run) data can be done as follows.

```bash
curl -s  'http://localhost:8080/api/sql/1/queryrun?query=$.*&array=true'  -H 'content-type: application/json' -H "X-Horreum-API-Key: $API_KEY"
{
  "valid": true,
  "jsonpath": "$.*",
  "errorCode": 0,
  "sqlState": null,
  "reason": null,
  "sql": null,
  "value": "[\"urn:acme:benchmark:0.1\", [{\"test\": \"Foo\", \"duration\": 10, \"requests\": 123}, {\"test\": \"Bar\", \"duration\": 20, \"requests\": 456}], \"defec8eddeadbeafcafebabeb16b00b5\", \"This gets lost by the transformer\"]}"
}
```

The response contains an enclosing JSON object that is common for Filter Query operations. The *value* property contains an array. The elements of the array contain the values of properties in the uploaded [Run](/docs/concepts/core-concepts/#run) JSON. Note the additional HTTP request query parameter named *array*. Setting *array* is necessary to return dynamically sized results.

To filter the above API operation to retrieve only the *results* property of the uploaded [Run](/docs/concepts/core-concepts/#run) object the *query* parameter is defined as `$.results`

```bash
curl -s  'http://localhost:8080/api/sql/1/queryrun?query=$.results&array=true'  -H 'content-type: application/json' -H "X-Horreum-API-Key: $API_KEY"
{
  "valid": true,
  "jsonpath": "$.results",
  "errorCode": 0,
  "sqlState": null,
  "reason": null,
  "sql": null,
  "value": "[[{\"test\": \"Foo\", \"duration\": 10, \"requests\": 123}, {\"test\": \"Bar\", \"duration\": 20, \"requests\": 456}]]"
}

```

The response JSON object this time contains the *results* property value JSON.
