import { DateTime } from "luxon"
import Api, { AnnotationDefinition, TimeseriesTarget } from "../../api"
import { fingerprintToString } from "../../utils"

function range(from: number, to: number) {
    return {
        from: new Date(DateTime.fromMillis(from).setZone("utc").toISO()),
        to: new Date(DateTime.fromMillis(to).setZone("utc").toISO()),
    }
}

export const fetchDatapoints = (
    variableIds: number[],
    fingerprint: unknown,
    from: number,
    to: number
): Promise<TimeseriesTarget[]> => {
    const query = {
        range: range(from, to),
        targets: variableIds.map(id => ({
            target: `${id};${fingerprintToString(fingerprint)}`,
            type: "timeseries",
            refId: "ignored",
        })),
    }
    return Api.grafanaServiceQuery(query)
}

export const fetchAnnotations = (
    variableId: number,
    fingerprint: unknown,
    from: number,
    to: number
): Promise<AnnotationDefinition[]> => {
    const query = {
        range: range(from, to),
        annotation: {
            query: variableId + ";" + fingerprintToString(fingerprint),
        },
    }
    return Api.grafanaServiceAnnotations(query)
}

export const fetchAllAnnotations = (
    variableIds: number[],
    fingerprint: unknown,
    from: number,
    to: number
): Promise<AnnotationDefinition[]> => {
    // TODO: let's create a bulk operation for these
    return Promise.all(variableIds.map(id => fetchAnnotations(id, fingerprint, from, to))).then(results =>
        results.flat()
    )
}
