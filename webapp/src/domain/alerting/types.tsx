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
