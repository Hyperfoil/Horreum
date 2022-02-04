export {}

export type ChangeDetection = {
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
    changeDetection: ChangeDetection[]
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

export type ChangeDetectionModelConfig = {
    name: string
    title: string
    description: string
    ui: ChangeDetectionModelConfigComponent[]
    defaults: any
}

export type ChangeDetectionModelConfigComponent = {
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
