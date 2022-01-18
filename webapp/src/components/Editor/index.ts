function replacer(_: any, value: any) {
    if (typeof value === "function") {
        return value.toString()
    }
    return value
}

export const toString = (obj: any): string => {
    return JSON.stringify(obj, replacer, 2)
}
