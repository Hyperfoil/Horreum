import {useMemo, useState, useRef, useContext} from "react"

import { Button, Checkbox, DescriptionList, DescriptionListDescription, DescriptionListGroup, DescriptionListTerm, Form, FormGroup, Modal, Progress } from "@patternfly/react-core"
import { NavLink } from "react-router-dom"

import {alertingApi, DatapointRecalculationStatus, DatasetInfo} from "../../api"
import TimeRangeSelect, { TimeRange } from "../../components/TimeRangeSelect"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";

type RecalculateModalProps = {
    title: string
    recalculate: string
    cancel: string
    message: string
    isOpen: boolean
    onClose(): void
    testId: number
    showLog?(): void
}

function isEmpty(value: any) {
    return !Array.isArray(value) || value.length === 0
}

function datasetsToLinks(datasets: DatasetInfo[] | undefined | null) {
    if (!datasets) {
        return null
    }
    // reverse sort
    return (
        <>
            {datasets
                .sort((a, b) => a.id - b.id)
                .slice(0, 10)
                .map((ds, i) => (
                    <>
                        {i !== 0 && ", "}
                        <NavLink to={`/run/${ds.runId}#dataset${ds.ordinal}`}>
                            {ds.runId}/{ds.ordinal}
                        </NavLink>
                    </>
                ))}
            {datasets.length > 10 && "..."}
        </>
    )
}

export default function RecalculateModal({ title, recalculate, cancel, message, isOpen, onClose, testId, showLog } : RecalculateModalProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [progress, setProgress] = useState(-1)
    const [debug, setDebug] = useState(false)
    const [clearDatapoints, setClearDatapoints] = useState(true)
    const [timeRange, setTimeRange] = useState<TimeRange>()
    const timer = useRef<number>()
    const [result, setResult] = useState<DatapointRecalculationStatus>()
    const close = () => {
        setProgress(-1)
        if (timer.current) {
            window.clearInterval(timer.current)
        }
        onClose()
    }
    const fetchProgress = () => {
        alertingApi.getRecalculationStatus(testId).then(
            response => {
                if (response.done) {
                    close()
                    if (response.errors !== 0 || !isEmpty(response.datasetsWithoutValue)) {
                        setResult(response)
                    }
                } else {
                    setProgress(response.percentage)
                }
            },
            error => {
                close()
                alerting.dispatchError(error,"RECALC_PROGRESS", "Cannot query recalculation progress")
            }
        )
    }
    const timeRangeOptions: TimeRange[] = useMemo(
        () => [
            { toString: () => "all" },
            { from: Date.now() -  15_811_200_000, to: undefined, toString: () => "last 6 months" },
            { from: Date.now() -   7_948_800_000, to: undefined, toString: () => "last 3 months" },
            { from: Date.now() - 31 * 86_400_000, to: undefined, toString: () => "last month" },
            { from: Date.now() -  7 * 86_400_000, to: undefined, toString: () => "last week" },
            { from: Date.now() -      86_400_000, to: undefined, toString: () => "last 24 hours" },
        ],
        []
    )
    return (
        <>
            <Modal
                variant="small"
                title={title}
                isOpen={isOpen}
                onClose={close}
                actions={
                    progress < 0
                        ? [
                              <Button
                                  key={1}
                                  variant="primary"
                                  onClick={() => {
                                      alertingApi.recalculateDatapoints(
                                          testId,
                                          clearDatapoints,
                                          debug,
                                          timeRange?.from,
                                          false,
                                          timeRange?.to
                                      ).then(
                                          _ => {
                                              timer.current = window.setInterval(fetchProgress, 1000)
                                          },
                                          error => {
                                              setProgress(-1)
                                              onClose()
                                              alerting.dispatchError(error,"RECALCULATION", "Failed to start recalculation")
                                          }
                                      )
                                      setProgress(0)
                                  }}
                              >
                                  {recalculate}
                              </Button>,
                              <Button key={2} variant="secondary" onClick={onClose}>
                                  {cancel}
                              </Button>,
                          ]
                        : [
                              <Button key={3} variant="secondary" onClick={close}>
                                  Continue recalculation in background...
                              </Button>,
                          ]
                }
            >
                {progress < 0 && (
                    <Form isHorizontal>
                        {message}
                        <FormGroup label="Runs from:" fieldId="timeRange">
                            <TimeRangeSelect selection={timeRange ?? timeRangeOptions[0]} onSelect={setTimeRange} options={timeRangeOptions} />
                        </FormGroup>
                        <FormGroup label="Recalculate Datapoints:" fieldId="recalcDatapoints">
                            <Checkbox id="recalcDatapoints" isChecked={clearDatapoints} onChange={(_event, val) => setClearDatapoints(val)} />
                        </FormGroup>
                        <FormGroup label="Save Debug logs:" fieldId="debug">
                            <Checkbox id="debug" isChecked={debug} onChange={(_event, val) => setDebug(val)}/>
                        </FormGroup>
                    </Form>
                )}
                {progress >= 0 && <Progress value={progress} title="Recalculating..." measureLocation="inside" />}
            </Modal>
            {result && (
                <Modal
                    variant="small"
                    title="Recalculation completed"
                    isOpen={!!result}
                    onClose={() => setResult(undefined)}
                    actions={[
                        <Button key={0} onClick={() => setResult(undefined)}>
                            Close
                        </Button>,
                        <Button
                            key={1}
                            variant="secondary"
                            isDisabled={!showLog}
                            onClick={() => {
                                setResult(undefined)
                                if (showLog) {
                                    showLog()
                                }
                            }}
                        >
                            Show log
                        </Button>,
                    ]}
                >
                    <DescriptionList isHorizontal aria-label="results-info">
                        <DescriptionListGroup>
                            <DescriptionListTerm>Total number of runs</DescriptionListTerm>
                            <DescriptionListDescription>{result.totalDatasets}</DescriptionListDescription>
                        </DescriptionListGroup>
                        <DescriptionListGroup>
                            <DescriptionListTerm>Datasets without value</DescriptionListTerm>
                            <DescriptionListDescription>{datasetsToLinks(result.datasetsWithoutValue)}</DescriptionListDescription>
                        </DescriptionListGroup>
                        <DescriptionListGroup>
                            <DescriptionListTerm>Value parsing errors</DescriptionListTerm>
                            <DescriptionListDescription>{result.errors}</DescriptionListDescription>
                        </DescriptionListGroup>
                    </DescriptionList>
                </Modal>
            )}
        </>
    )
}
