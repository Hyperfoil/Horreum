---
title: Extracting data via labelValues
description: Query label values from Horreum tests, runs, and datasets using the labelValues API
date: 2026-06-19
weight: 4
---

Horreum extracts metrics and metadata from uploaded runs using [Labels](/docs/tasks/define-schema-and-views/).
Each calculated value is stored as a **label value** on a [Dataset](/docs/concepts/core-concepts/#dataset).
The `/labelValues` endpoints expose those values over HTTP so you can build dashboards, scripts, and CI integrations without downloading full run payloads.

## Endpoints

| Endpoint | Scope |
|---|---|
| `GET /api/test/{id}/labelValues` | All datasets across all runs in a test |
| `GET /api/run/{id}/labelValues` | All datasets within a single run |
| `GET /api/dataset/{datasetId}/labelValues` | Label values for one dataset |

The test-level endpoint is the most common choice when comparing results across many uploads (for example, trend charts in Grafana or nightly regression scripts).
The run- and dataset-level endpoints are useful when you already know which upload you are interested in.

All endpoints accept the same query parameters unless noted otherwise.

## Authentication

Pass your API key in the `X-Horreum-API-Key` header:

```bash
export HORREUM_URL="https://horreum.example.com"
export HORREUM_API_KEY="HUSR_00000000_0000_0000_0000_000000000000"
export TEST_ID=101

curl -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues"
```

## Response structure

Each endpoint returns a JSON **array**. Every element describes one dataset:

```json
[
  {
    "runId": 201,
    "datasetId": 301,
    "start": "2026-06-01T08:15:30.123Z",
    "stop": "2026-06-01T08:45:12.456Z",
    "values": {
      "deployment_version": "1.20.0",
      "parameters_test_total": 1000,
      "measurements_tektonPipelinesController_cpu_mean": 0.42
    }
  }
]
```

| Field | Description |
|---|---|
| `runId` | ID of the uploaded run that produced this dataset |
| `datasetId` | Unique dataset ID (a single run may produce multiple datasets when transformers split results) |
| `start`, `stop` | Dataset time range |
| `values` | Map of label names to extracted values (strings, numbers, booleans, or nested JSON depending on the label definition) |

Most integrations only need the `values` map. `runId`, `datasetId`, and timestamps are useful for deduplication, drill-down links, and time-based filtering.

## Label type selection

By default the test endpoint returns both **filtering** labels (metadata used to partition comparisons) and **metric** labels (measurements tracked for change detection).
Use these query parameters to narrow the payload:

| Parameter | Default | Description |
|---|---|---|
| `filtering` | `true` | Include labels marked as filtering labels in the schema |
| `metrics` | `true` | Include labels marked as metric labels in the schema |

To retrieve only metrics (typical for charting):

```bash
curl -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues?filtering=false&metrics=true"
```

## Filtering datasets

The `filter` query parameter selects which datasets are returned. An empty filter (`{}`, the default) returns all datasets that match the other criteria.

Horreum supports three filter styles.

### JSON object filter (exact match)

Pass a JSON object whose keys are label names and values are the required label values.
All conditions must match (logical AND).

```bash
# Only datasets where deployment_version is "1.20.0" and parameters_test_total is 1000
curl -G -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  --data-urlencode 'filter={"deployment_version":"1.20.0","parameters_test_total":1000}' \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues"
```

If no dataset matches every condition, the response is an empty array.

### Multi-value filter (`multiFilter`)

Dashboard variables (for example Grafana multi-select) often send arrays of acceptable values.
By default Horreum treats an array as an exact label value.
Set `multiFilter=true` to match **any** value in the array instead:

```bash
# Match datasets where deployment_haConfig_haEnabled is either true or false
curl -G -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  --data-urlencode 'multiFilter=true' \
  --data-urlencode 'filter={"deployment_haConfig_haEnabled":[true,false]}' \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues"
```

This is the pattern used by the [Grafana JSON API tutorial](/docs/tutorials/grafana/) when wiring template variables.

### JSONPath filter

Instead of an object, pass a JSONPath expression as a quoted string.
The expression must evaluate to a non-null value for the dataset to be included.
Use label names as JSONPath roots (the same names that appear in `values`):

```bash
# Only datasets where measurements_tektonPipelinesController_cpu_mean is below 0.5
curl -G -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  --data-urlencode 'filter=$.measurements_tektonPipelinesController_cpu_mean ? (@ < 0.5)' \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues"
```

Another example using a numeric range:

```bash
curl -G -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  --data-urlencode 'filter=$.parameters_test_total ? (@ >= 500 && @ <= 2000)' \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues"
```

Invalid JSONPath expressions return HTTP 400.

### Time-based filters (test endpoint only)

Restrict results to datasets whose `start` timestamp falls within a range:

| Parameter | Format | Description |
|---|---|---|
| `after` | ISO-8601 timestamp or epoch milliseconds | Include datasets starting after this time |
| `before` | ISO-8601 timestamp or epoch milliseconds | Include datasets starting before this time |

```bash
# Datasets from the last 7 days
AFTER=$(date -u -d '7 days ago' +%Y-%m-%dT%H:%M:%SZ)
curl -G -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  --data-urlencode "after=$AFTER" \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues"
```

Combine time filters with object filters to, for example, chart controller CPU for a specific OpenShift version over the past month:

```bash
curl -G -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  --data-urlencode 'filter={"deployment_version":"1.20.0"}' \
  --data-urlencode "after=$AFTER" \
  --data-urlencode 'include=measurements_tektonPipelinesController_cpu_mean,deployment_version,started' \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues"
```

## Include and exclude labels

Tests with many labels can produce large responses.
Use `include` and `exclude` to control which labels appear in each dataset's `values` map.

### Include

Return only the listed labels:

```bash
curl -G -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  --data-urlencode 'include=measurements_tektonPipelinesController_cpu_mean,measurements_tektonPipelinesController_memory_mean' \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues"
```

Multiple labels can be passed as repeated parameters or as a comma-separated list:

```bash
curl -G -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  --data-urlencode 'include=labelFoo' \
  --data-urlencode 'include=labelBar' \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues"

# equivalent
curl -G -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  --data-urlencode 'include=labelFoo,labelBar' \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues"
```

### Exclude

Remove specific labels while keeping everything else:

```bash
curl -G -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  --data-urlencode 'exclude=metadata_env_BUILD_ID,metadata_env_SUBJOB_BUILD_ID' \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues"
```

### Combining include and exclude

When both are present:

- `include` defines the candidate set of labels.
- Any label appearing in both `include` and `exclude` is **removed** from the response.
- If every `include` label is also in `exclude`, Horreum falls back to returning all labels **except** those in `exclude`.

This prevents explicitly excluded labels from leaking into the response even when they were also listed in `include`.

## Ordering

### Test endpoint

| Parameter | Values | Default |
|---|---|---|
| `sort` | `start`, `stop`, or empty | Empty (sort by `runId` descending) |
| `direction` | `Ascending` or `Descending` | `Ascending` |

```bash
# Oldest datasets first
curl -G -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  --data-urlencode 'sort=start' \
  --data-urlencode 'direction=Ascending' \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues"

# Most recent datasets first
curl -G -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  --data-urlencode 'sort=stop' \
  --data-urlencode 'direction=Descending' \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues"
```

When no `sort` is provided, results are ordered by `runId` descending (newest runs first).

### Run endpoint

The run endpoint orders by `datasetId`. The `sort` parameter is ignored.

## Pagination

Use `limit` and `page` to paginate through large result sets.
Both parameters apply after filtering and ordering.

| Parameter | Default | Description |
|---|---|---|
| `limit` | unlimited | Maximum number of datasets to return |
| `page` | `0` | Zero-based page index (`page=1` skips the first `limit` results) |

```bash
# First page: 10 most recent datasets
curl -G -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  --data-urlencode 'limit=10' \
  --data-urlencode 'page=0' \
  --data-urlencode 'sort=stop' \
  --data-urlencode 'direction=Descending' \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues"

# Second page
curl -G -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  --data-urlencode 'limit=10' \
  --data-urlencode 'page=1' \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues"
```

## Real-world examples

The examples below use label names from the OpenShift Pipelines performance tests.
Adapt the test ID and label names to your own Horreum schema.

### Compare controller CPU across OpenShift versions

```bash
curl -G -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  --data-urlencode 'filter={"parameters_test_total":1000,"deployment_haConfig_haEnabled":false}' \
  --data-urlencode 'include=deployment_version,measurements_tektonPipelinesController_cpu_mean,started' \
  --data-urlencode 'sort=start' \
  --data-urlencode 'direction=Ascending' \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues" \
  | jq '.[] | {version: .values.deployment_version, cpu: .values.measurements_tektonPipelinesController_cpu_mean, started: .values.started}'
```

### Fetch the latest HA run for a given controller type

```bash
curl -G -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  --data-urlencode 'filter={"deployment_haConfig_haEnabled":true,"deployment_haConfig_controllerType":"statefulsets"}' \
  --data-urlencode 'limit=1' \
  --data-urlencode 'sort=stop' \
  --data-urlencode 'direction=Descending' \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues"
```

### Export metrics for an external spreadsheet

Retrieve only metric labels, excluding verbose metadata:

```bash
curl -G -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  --data-urlencode 'filtering=false' \
  --data-urlencode 'metrics=true' \
  --data-urlencode 'exclude=metadata_env_BUILD_ID,metadata_env_SUBJOB_BUILD_ID' \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues" \
  > label-values-export.json
```

### Filter high CPU outliers with JSONPath

```bash
curl -G -s -H "X-Horreum-API-Key: $HORREUM_API_KEY" \
  --data-urlencode 'filter=$.measurements_apiserver_cpu_max ? (@ > 2.0)' \
  --data-urlencode 'include=deployment_version,deployment_ocpVersion,measurements_apiserver_cpu_max,started' \
  "$HORREUM_URL/api/test/$TEST_ID/labelValues"
```

### Grafana dashboard query

When using the [JSON API Grafana plugin](/docs/tutorials/grafana/), point the panel query at:

```
/test/${testId}/labelValues?filter=${filter}&multiFilter=true&include=${metric}
```

Define Grafana variables for `testId`, `filter` (a JSON object built from dashboard filters), and `metric` (the label to chart).
Use the **Extract fields** transform on the `values` column to flatten metrics into Grafana's dataframe format.

## Related documentation

- [Define schema and views](/docs/tasks/define-schema-and-views/) — configure the labels that populate `values`
- [Use data in Grafana](/docs/tutorials/grafana/) — end-to-end Grafana integration walkthrough
- [API reference](/docs/reference/api-reference/) — full OpenAPI specification including all `/labelValues` parameters
