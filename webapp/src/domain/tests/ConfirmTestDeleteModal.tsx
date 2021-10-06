import { useState, useEffect } from "react"
import { useDispatch } from "react-redux"
import { runCount, RunCount } from "../runs/api"
import { alertAction } from "../../alerts"
import { Bullseye, Button, ButtonVariant, Modal, TextInput, Spinner } from "@patternfly/react-core"

type ConfirmTestDeleteModalProps = {
    isOpen: boolean
    onClose(): void
    onDelete(): void
    testId?: number
    testName: string
}

function ConfirmTestDeleteModal(props: ConfirmTestDeleteModalProps) {
    const [runsToDelete, setRunsToDelete] = useState("")
    const [runs, setRuns] = useState<RunCount>()
    const validationResult =
        runsToDelete.length === 0
            ? "default"
            : runs?.active === 0 || runsToDelete === runs?.active.toString()
            ? "success"
            : "error"
    const dispatch = useDispatch()
    useEffect(() => {
        if (props.testId && props.isOpen) {
            runCount(props.testId).then(setRuns, error => {
                dispatch(alertAction("FETCH_RUN_COUNTS", "Failed to fetch run counts", error))
                setRuns({ active: -1, trashed: -1, total: -1 })
            })
        }
    }, [props.testId, props.isOpen])
    return (
        <Modal
            isOpen={props.isOpen}
            onClose={() => {
                setRunsToDelete("")
                props.onClose()
            }}
            title="Confirm test delete"
            showClose={true}
            actions={[
                <Button
                    key="Delete"
                    variant={ButtonVariant.danger}
                    isDisabled={!runs || (validationResult !== "success" && runs.active > 0)}
                    onClick={() => {
                        props.onDelete()
                        setRunsToDelete("")
                        props.onClose()
                    }}
                >
                    Delete
                </Button>,
                <Button
                    key="Cancel"
                    variant={ButtonVariant.secondary}
                    onClick={() => {
                        setRunsToDelete("")
                        props.onClose()
                    }}
                >
                    Cancel
                </Button>,
            ]}
        >
            {!runs && (
                <Bullseye>
                    <Spinner size="xl" />
                </Bullseye>
            )}
            {runs && runs.active > 0 && (
                <>
                    This test has {numRuns(runs.active)} runs (and {numRuns(runs.trashed)} trashed). Please type{" "}
                    {numRuns(runs.active)} into the field below to confirm trashing all runs.
                    <br />
                    <TextInput
                        value={runsToDelete}
                        isRequired
                        type="text"
                        id="runsToDelete"
                        validated={validationResult}
                        onChange={setRunsToDelete}
                    />
                </>
            )}
            {runs && runs.active <= 0 && <>Do you really want to delete test {props.testName}?</>}
        </Modal>
    )
}

function numRuns(val: number) {
    if (val < 0) {
        return "?"
    }
    return val
}

export default ConfirmTestDeleteModal
