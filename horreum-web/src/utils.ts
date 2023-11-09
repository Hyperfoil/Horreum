import { DateTime } from "luxon"

export function noop() {
    /* noop */
}

export function isEmpty(obj: any): boolean {
    for (const key in obj) {
        if (Object.prototype.hasOwnProperty.call(obj, key)) return false
    }
    return true
}

function ensureISO(timestamp: string) {
    return timestamp.replace(" ", "T")
}

export function formatDate(timestamp: any): string {
    let datetime
    if (!timestamp) {
        return "--"
    } else if (typeof timestamp === "string") {
        datetime = DateTime.fromISO(ensureISO(timestamp))
    } else if (typeof timestamp === "number") {
        datetime = DateTime.fromMillis(timestamp)
    } else if (timestamp instanceof Date) {
        datetime = DateTime.fromJSDate(timestamp)
    } else {
        return String(timestamp)
    }
    return datetime.toFormat("yyyy-LL-dd")
}

export function formatDateTime(timestamp: any): string {
    let datetime
    if (!timestamp) {
        return "--"
    } else if (typeof timestamp === "string") {
        datetime = DateTime.fromISO(ensureISO(timestamp))
    } else if (typeof timestamp === "number") {
        datetime = DateTime.fromMillis(timestamp)
    } else if (timestamp instanceof Date) {
        datetime = DateTime.fromJSDate(timestamp)
    } else {
        return String(timestamp)
    }
    return datetime.toFormat("yyyy-LL-dd HH:mm:ss")
}

export function toEpochMillis(timestamp: any): number {
    if (!timestamp) {
        return 0
    } else if (typeof timestamp === "string") {
        return DateTime.fromISO(ensureISO(timestamp)).toMillis()
    } else if (typeof timestamp === "number") {
        if (Number.isNaN(timestamp)) {
            return 0
        }
        return timestamp
    } else if (timestamp instanceof Date) {
        return DateTime.fromJSDate(timestamp).toMillis()
    } else {
        return 0
    }
}

export function durationToMillis(duration: string): number | undefined {
    if (duration.length === 0) {
        return undefined
    }
    duration = duration.replaceAll(",", " ").trim().toLowerCase()
    let value = 0
    let failed = false
    const units = ["s", "m", "h", "d"]
    const multiplier = [1000, 60_000, 3600_000, 86_400_000]
    units.forEach((u, i) => {
        if (duration.endsWith(u)) {
            duration = duration.substring(0, duration.length - 1).trimEnd()
            const lastSpace = duration.lastIndexOf(" ")
            const num = parseInt(duration.substring(lastSpace + 1))
            if (isNaN(num)) {
                // console.error("Cannot parse '" + u + "' from " + original)
                failed = true
                return
            }
            value += num * multiplier[i]
            duration = duration.substring(0, lastSpace).trimEnd()
        }
    })
    if (failed || duration.length > 0) {
        // console.error("Cannot parse '" + original + "', residuum is '" + duration + "'")
        return undefined
    }
    return value
}

export function millisToDuration(duration: number): string {
    let text = ""
    const units = ["d", "h", "m", "s"]
    const multiplier = [86_400_000, 3600_000, 60_000, 1000]
    multiplier.forEach((m, i) => {
        if (duration >= m) {
            const num = Math.trunc(duration / m)
            text += num + units[i] + " "
            duration -= num * m
        }
    })
    // ignore ms
    return text
}

export function interleave<T>(arr: T[], inBetween: (i: number) => T) {
    const narr: T[] = []
    for (let i = 0; i < arr.length - 1; ++i) {
        narr.push(arr[i])
        narr.push(inBetween(i))
    }
    narr.push(arr[arr.length - 1])
    return narr
}

export type PaginationInfo = {
    page: number
    perPage: number
    sort: string
    direction: string //TODO:: This should be an enum
}

export function paginationParams(pagination: PaginationInfo) {
    return `page=${pagination.page}&limit=${pagination.perPage}&sort=${pagination.sort}&direction=${pagination.direction}`
}

export function deepEquals(x: any, y: any) {
    if (x === y) {
        return true
    } else if (typeof x == "object" && x != null && typeof y == "object" && y != null) {
        if (Object.keys(x).length != Object.keys(y).length) return false

        for (const prop in x) {
            if (Object.prototype.hasOwnProperty.call(y, prop)) {
                if (!deepEquals(x[prop], y[prop])) return false
            } else return false
        }

        return true
    } else return false
}

export function fingerprintToString(fingerprint: unknown) {
    if (!fingerprint) {
        return ""
    }
    return JSON.stringify(fingerprint);
}
