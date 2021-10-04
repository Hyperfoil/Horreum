import { useState, useRef } from "react"

import { useDispatch } from "react-redux"

import { Button, Checkbox, Form, FormGroup, Modal, Progress } from "@patternfly/react-core"
import { Table, TableBody } from "@patternfly/react-table"
import { NavLink } from "react-router-dom"

import { recalculate, recalculateProgress } from "./api"
import { alertAction } from "../../alerts"
import TimeRangeSelect, { TimeRange } from "../../components/TimeRangeSelect"

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

type RecalculationResult = {
    totalRuns: number
    errors: number
    runsWithoutAccessor: number[]
    runsWithoutValue: number[]
}

function runsToLinks(ids: number[]) {
    // reverse sort
    return (
        <>
            {ids
                .sort((a, b) => a - b)
                .slice(0, 10)
                .map((id, i) => (
                    <>
                        {i !== 0 && ", "}
                        <NavLink to={`/run/${id}`}>{id}</NavLink>
                    </>
                ))}
            {ids.length > 10 && "..."}
        </>
    )
}

export default function RecalculateModal(props: RecalculateModalProps) {
    const [progress, setProgress] = useState(-1)
    const dispatch = useDispatch()
    const [debug, setDebug] = useState(false)
    const [timeRange, setTimeRange] = useState<TimeRange>()
    const timer = useRef<number>()
    const [result, setResult] = useState<RecalculationResult>()
    const close = () => {
        setProgress(-1)
        if (timer.current) {
            window.clearInterval(timer.current)
        }
        props.onClose()
    }
    const fetchProgress = () => {
        recalculateProgress(props.testId).then(
            response => {
                if (response.done) {
                    close()
                    if (
                        response.errors !== 0 ||
                        !isEmpty(response.runsWithoutAccessor) ||
                        !isEmpty(response.runsWithoutValue)
                    ) {
                        setResult(response)
                    }
                } else {
                    setProgress(response.percentage)
                }
            },
            error => {
                close()
                dispatch(alertAction("RECALC_PROGRESS", "Cannot query recalculation progress", error))
            }
        )
    }
    return (
        <>
            <Modal
                variant="small"
                title={props.title}
                isOpen={props.isOpen}
                onClose={close}
                actions={
                    progress < 0
                        ? [
                              <Button
                                  key={1}
                                  variant="primary"
                                  onClick={() => {
                                      setProgress(0)
                                      recalculate(props.testId, debug, timeRange?.from, timeRange?.to).then(
                                          _ => {
                                              timer.current = window.setInterval(fetchProgress, 1000)
                                          },
                                          error => {
                                              setProgress(-1)
                                              props.onClose()
                                              dispatch(
                                                  alertAction("RECALCULATION", "Failed to start recalculation", error)
                                              )
                                          }
                                      )
                                  }}
                              >
                                  {props.recalculate}
                              </Button>,
                              <Button key={2} variant="secondary" onClick={props.onClose}>
                                  {props.cancel}
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
                        {props.message}
                        <FormGroup label="Runs from:" fieldId="timeRange">
                            <TimeRangeSelect selection={timeRange} onSelect={setTimeRange} />
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
                            isDisabled={!props.showLog}
                            onClick={() => {
                                setResult(undefined)
                                if (props.showLog) {
                                    props.showLog()
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
                            ["Total number of runs", result.totalRuns],
                            ["Runs without accessor", runsToLinks(result.runsWithoutAccessor)],
                            ["Runs without value", runsToLinks(result.runsWithoutValue)],
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
