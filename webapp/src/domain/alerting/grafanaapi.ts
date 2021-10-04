import { fetchApi } from "../../services/api"
import { DateTime } from "luxon"

const base = "/api/grafana"
const endPoints = {
    query: () => `${base}/query`,
    annotations: () => `${base}/annotations`,
}

export type TimeseriesTarget = {
    target: string
    datapoints: number[][] // array of [value, timestamp]
    // extra (not used by Grafana)
    variableId?: number
}

function range(from: number, to: number) {
    return {
        from: DateTime.fromMillis(from).setZone("utc").toISO(),
        to: DateTime.fromMillis(to).setZone("utc").toISO(),
    }
}

export const fetchDatapoints = (
    variableIds: number[],
    tags: string,
    from: number,
    to: number
): Promise<TimeseriesTarget[]> => {
    const query = {
        range: range(from, to),
        targets: variableIds.map(id => ({
            target: `${id};${tags}`,
            type: "timeseries",
            refId: "ignored",
        })),
    }
    return fetchApi(endPoints.query(), query, "post")
}

export type Annotation = {
    title: string
    text: string
    isRegion: boolean
    time: number
    timeEnd: number
    tags: string[]
    // extra (not used by Grafana)
    changeId?: number
    variableId?: number
    runId?: number
}

export const fetchAnnotations = (variableId: number, tags: string, from: number, to: number): Promise<Annotation[]> => {
    const query = {
        range: range(from, to),
        annotation: {
            query: variableId + ";" + tags,
        },
    }
    return fetchApi(endPoints.annotations(), query, "post")
}

export const fetchAllAnnotations = (
    variableIds: number[],
    tags: string,
    from: number,
    to: number
): Promise<Annotation[]> => {
    return Promise.all(variableIds.map(id => fetchAnnotations(id, tags, from, to))).then(results => results.flat())
}
