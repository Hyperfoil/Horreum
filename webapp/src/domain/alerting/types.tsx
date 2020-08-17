export {}

export type Variable = {
    id: number,
    name: string,
    testid: number,
    accessors: string,
    calculation?: string,
    maxWindow: number,
    deviationFactor: number,
    confidence: number,
}

export type Dashboard = {
    testId: number,
    uid: string,
    url: string,
    variables: Variable[],
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