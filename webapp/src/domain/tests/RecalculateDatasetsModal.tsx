import { useCallback, useState, useRef } from "react"
import { useDispatch, useSelector } from "react-redux"
import { getRecalculationStatus, recalculateDatasets, RecalculationStatus } from "./api"
import { updateRunsAndDatasetsAction } from "./actions"
import { get } from "./selectors"
import { dispatchError } from "../../alerts"
import { Bullseye, Button, Modal, Progress, Spinner } from "@patternfly/react-core"

type RecalculateDatasetsModalProps = {
    testId: number
    isOpen: boolean
    onClose(): void
}

export default function RecalculateDatasetsModal(props: RecalculateDatasetsModalProps) {
    const test = useSelector(get(props.testId))
    const [progress, setProgress] = useState(-1)
    const [status, setStatus] = useState<RecalculationStatus>()
    const timerId = useRef<number>()
    const dispatch = useDispatch()
    const totalRuns = status ? status.totalRuns : test?.runs
    const onClose = useCallback(() => {
        if (timerId.current) {
            clearInterval(timerId.current)
            timerId.current = undefined
        }
        setProgress(-1)
        setStatus(undefined)
        props.onClose()
    }, [])
    if (props.isOpen) {
        console.log("Timer: " + timerId)
    }
    return (
        <Modal
            title={`Recalculate datasets for test ${test?.name || "<unknown test>"}`}
            variant="small"
            showClose={true}
            isOpen={props.isOpen}
            onClose={onClose}
            actions={
                progress < 0
                    ? [
                          <Button
                              key="recalculate"
                              onClick={() => {
                                  setProgress(0)
                                  recalculateDatasets(props.testId)
                                      .then(() => {
                                          timerId.current = window.setInterval(() => {
                                              getRecalculationStatus(props.testId)
                                                  .then(status => {
                                                      setStatus(status)
                                                      setProgress(status.finished)
                                                      dispatch(
                                                          updateRunsAndDatasetsAction(
                                                              props.testId,
                                                              status.totalRuns,
                                                              status.datasets
                                                          )
                                                      )
                                                      if (status.finished === status.totalRuns) {
                                                          onClose()
                                                      }
                                                  })
                                                  .catch(error => {
                                                      dispatchError(
                                                          dispatch,
                                                          error,
                                                          "RECALC_DATASETS",
                                                          "Failed to get recalculation status"
                                                      )
                                                      onClose()
                                                  })
                                          }, 1000)
                                      })
                                      .catch(error => {
                                          dispatchError(
                                              dispatch,
                                              error,
                                              "RECALC_DATASETS",
                                              "Failed to start recalculation"
                                          )
                                          onClose()
                                      })
                              }}
                          >
                              Recalculate
                          </Button>,
                          <Button key="cancel" onClick={() => onClose()} variant="secondary">
                              Cancel
                          </Button>,
                      ]
                    : []
            }
        >
            {!test && (
                <Bullseye>
                    <Spinner size="xl" />
                </Bullseye>
            )}
            {progress < 0 && (
                <div style={{ marginBottom: "16px" }}>
                    This test has {test?.runs || "<unknown number"} of runs; do you want to recalculate all datasets?
                </div>
            )}
            {progress >= 0 && (
                <Progress
                    min={0}
                    max={totalRuns}
                    measureLocation="inside"
                    value={progress}
                    valueText={`${progress}/${totalRuns}`}
                />
            )}
        </Modal>
    )
}
