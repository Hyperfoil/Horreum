import {useMemo, useState, useRef, useContext} from "react"

import { Button, Checkbox, Form, FormGroup, Modal, Progress } from "@patternfly/react-core"
import { Table, TableBody } from "@patternfly/react-table"
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
                            {ds.runId}/{ds.ordinal + 1}
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
            { from: Date.now() - 31 * 86_400_000, to: undefined, toString: () => "last month" },
            { from: Date.now() - 7 * 86_400_000, to: undefined, toString: () => "last week" },
            { from: Date.now() - 86_400_000, to: undefined, toString: () => "last 24 hours" },
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
                                      setProgress(0)
                                      alertingApi.recalculateDatapoints(
                                          testId,
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
                            <TimeRangeSelect selection={timeRange} onSelect={setTimeRange} options={timeRangeOptions} />
                        </FormGroup>
                        <FormGroup label="Debug logs:" fieldId="debug">
                            <Checkbox id="debug" isChecked={debug} onChange={setDebug} label="Write debug logs" />
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
                    <Table
                        aria-label="Results table"
                        variant="compact"
                        cells={["Category", "Value"]}
                        rows={[
                            ["Total number of runs", result.totalDatasets],
                            ["Datasets without value", datasetsToLinks(result.datasetsWithoutValue)],
                            ["Value parsing errors", result.errors],
                        ]}
                    >
                        <TableBody />
                    </Table>
                </Modal>
            )}
        </>
    )
}
