import {useCallback, useEffect, useState, useRef, useContext} from "react"
import { Bullseye, Button, Modal, Progress, Spinner } from "@patternfly/react-core"
import {fetchTest, RecalculationStatus, testApi, TestStorage, updateRunsAndDatasetsAction} from "../../api"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";


type RecalculateDatasetsModalProps = {
    testId: number
    isOpen: boolean
    onClose(): void
}

export default function RecalculateDatasetsModal(props: RecalculateDatasetsModalProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [test, setTest] = useState<TestStorage | undefined>( undefined)
    const [progress, setProgress] = useState(-1)
    const [status, setStatus] = useState<RecalculationStatus>()
    const timerId = useRef<number>()
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

    useEffect(() => {
        if (!props.isOpen) {
            return
        }
        fetchTest(props.testId, alerting).then(setTest)
    }, [props.testId]);

    useEffect(() => {
        if (!props.isOpen) {
            return
        }
        if (test?.runs === undefined) {
            testApi.getRecalculationStatus(props.testId).then(status => {
                updateRunsAndDatasetsAction(props.testId, status.totalRuns, status.datasets)
            })
        }
    }, [test, props.isOpen])
    return (
        <Modal
            title={`Re-transform datasets for test ${test?.name || "<unknown test>"}`}
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
                                  testApi.recalculateDatasets(props.testId)
                                      .then(() => {
                                          timerId.current = window.setInterval(() => {
                                              testApi.getRecalculationStatus(props.testId)
                                                  .then(status => {
                                                      setStatus(status)
                                                      setProgress(status.finished)
                                                      updateRunsAndDatasetsAction(
                                                          props.testId,
                                                          status.totalRuns,
                                                          status.datasets
                                                      )
                                                      if (status.finished === status.totalRuns) {
                                                          onClose()
                                                      }

                                                  })
                                                  .catch(error => {
                                                      alerting.dispatchError(
                                                          error,
                                                          "RECALC_DATASETS",
                                                          "Failed to get recalculation status"
                                                      )
                                                      onClose()
                                                  })
                                          }, 1000)
                                      })
                                      .catch(error => {
                                          alerting.dispatchError(
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
                    This test has {test?.runs || "<unknown number>"} of runs; do you want to recalculate all datasets?
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
