export {}

export type RegressionDetection = {
    id: number
    model: string
    config: any
}

export type Variable = {
    id: number
    name: string
    group?: string
    testid: number
    accessors: string
    calculation?: string
    regressionDetection: RegressionDetection[]
}

export type Panel = {
    name: string
    variables: Variable[]
}

// Maps to AlertingService.DashboardInfo
export type Dashboard = {
    testId: number
    uid: string
    url: string
    panels: Panel[]
}

export type DataPoint = {
    id: number
    runId: number
    timestamp: number
    value: number
    variable: Variable
}

export type Change = {
    id: number
    confirmed: boolean
    description: string
    variable?: Variable
    runId: number
    timestamp: number
}

export type RegressionModelConfig = {
    name: string
    title: string
    description: string
    ui: RegressionModelConfigComponent[]
    defaults: any
}

export type RegressionModelConfigComponent = {
    name: string
    title: string
    description: string
    type: "LOG_SLIDER" | "ENUM"
    properties: LogSliderProperties | EnumProperties
}

export type LogSliderProperties = {
    scale: number
    min: number
    max: number
    unit: string
}

export type EnumProperties = {
    options: Map<string, string>
}
