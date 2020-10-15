export {}

export type Variable = {
    id: number,
    name: string,
    group?: string,
    order: number,
    testid: number,
    accessors: string,
    calculation?: string,
    maxDifferenceLastDatapoint: number,
    minWindow: number,
    maxDifferenceFloatingWindow: number,
    floatingWindow: number,
}

export type Panel = {
    name: string,
    variables: Variable[],
}

export type Dashboard = {
    testId: number,
    uid: string,
    url: string,
    panels: Panel[],
}

export type DataPoint = {
    id: number,
    runId: number,
    timestamp: number,
    value: number,
    variable: Variable,
}

export type Change = {
    id: number,
    confirmed: boolean,
    description: string,
    variable?: Variable,
    runId: number,
    timestamp: number,
}