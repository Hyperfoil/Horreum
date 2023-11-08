
export const EXPERIMENT_RESULT_NEW = "experiment_result/new"
export const CHANGE_NEW = "change/new"
export const testEventTypes = [
    [
        "run/new",
        `{
  "id": 123,
  "data": ...,
  "start": "2022-08-29T12:28:14+02:00",
  "stop": "2022-08-29T12:28:14+02:00",
  "owner": "dev-team",
  "access": 0,
  "schema": {
    "1": "urn:my-schema:1.0"
  },
  "testid": 10,
  "testname": "My test",
  "description": null,
}`,
    ],
    [
        CHANGE_NEW,
        `{
  "change": {
    "id": 123,
    "variable": {
        "id": 456,
        "testId": 789,
        "name": "my-variable",
        "group": null,
        ...
    },
    "timestamp": "2022-08-29T12:28:14+02:00",
    "dataset": {
        "id": 1234,
        "runId": 999,
        "ordinal": 0,
        "testId": 789
    },
    "confirmed": false,
    "description": "Some generated description"
  },
  "testName": "My test",
  "dataset": ...,
  "notify": true
}`,
    ],
    [
        EXPERIMENT_RESULT_NEW,
        `{
  "profile": {
    "id": 123,
    "name": "pull-request",
    ...
  },
  "datasetInfo": {
    "id": 1234,
    "runId": 999,
    "ordinal": 0,
    "testId": 456,
  },
  "baseline": [
    {
      "id": 1000,
      "runId": 500,
      "ordinal": 1,
      "testId": 456,
    },
    ...
  ],
  "extraLabels": {
      "some-label": 123456,
      "another-label": "http://ci.example.com/123"
  }
  "results": {
      "my-variable": {
        "overall": "BETTER",
        "experimentValue": 123,
        "baselineValue": 100,
        "result": "+23%"
      },
      "another-variable": ...
  }
}`,
    ],
]
export const globalEventTypes = [
    ...testEventTypes,
    [
        "test/new",
        `{
  "id": 10,
  "name": "My test",
  "folder": null,
  "description": "",
  "owner": "dev-team",
  "access": 0
}`,
    ],
]
