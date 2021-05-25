import React, { useState } from 'react'
import {
    Button,
    Chip,
    DropdownItem,
    Modal,
    TextInput,
    Tooltip,
} from '@patternfly/react-core';
import moment from 'moment'
import { useDispatch, useSelector } from 'react-redux'

import { Run, RunsDispatch } from './reducers';
import { resetToken, dropToken, updateAccess, trash, updateDescription } from './actions';
import ActionMenu, { DropdownItemProvider } from '../../components/ActionMenu';
import { alertAction } from '../../alerts';
import { formatDateTime, toEpochMillis } from '../../utils'
import { useTester } from '../../auth'


export function Description(description: string) {
    return (<>
        <span style={{
            whiteSpace: "nowrap",
            overflow: "hidden",
            textOverflow: "ellipsis"
        }}>{description}</span>
    </>)
}

export const ExecutionTime = (run: Run) =>
    (<Tooltip isContentLeftAligned content={
        <table style={{ width: "300px" }}><tbody>
            <tr><td>Started:</td><td>{formatDateTime(run.start)}</td></tr>
            <tr><td>Finished:</td><td>{formatDateTime(run.stop)}</td></tr>
        </tbody></table>
    }>
        <span>{moment(toEpochMillis(run.stop)).fromNow()}</span>
    </Tooltip>)

export function Menu(run: Run) {
    const [updateDescriptionOpen, setUpdateDescriptionOpen] = useState(false)
    const dispatch = useDispatch()
    const thunkDispatch = useDispatch<RunsDispatch>()

    let onDelete = undefined
    const extras: DropdownItemProvider[] = [];
    if (run.trashed) {
        extras.push(closeMenuFunc => (
            <DropdownItem key="restore"
                onClick={() => thunkDispatch(trash(run.id, run.testid, false)).then(
                    _ => { closeMenuFunc() },
                    e => dispatch(alertAction("RUN_TRASH", "Failed to restore run ID " + run.id, e))
                )}
            >Restore</DropdownItem>
        ))
    } else {
        onDelete = (id: number) => thunkDispatch(trash(id, run.testid)).catch(
            e => dispatch(alertAction("RUN_TRASH", "Failed to trash run ID " + id, e))
        )
    }
    const isTester = useTester(run.owner)
    if (isTester) {
        extras.push(closeMenuFunc => (
            <DropdownItem key="updateDescription"
                onClick={() => {
                    closeMenuFunc()
                    setUpdateDescriptionOpen(true)
                }}
            >Edit description</DropdownItem>
        ))
    }
    return (<>
        <ActionMenu
            id={run.id}
            description={ "run " + run.id }
            owner={ run.owner }
            access={ run.access }
            token={ run.token || undefined }
            tokenToLink={ (id, token) => "/run/" + id + "?token=" + token }
            onTokenReset={ id => dispatch(resetToken(id, run.testid)) }
            onTokenDrop={ id => dispatch(dropToken(id, run.testid)) }
            onAccessUpdate={ (id, owner, access) => dispatch(updateAccess(id, run.testid, owner, access)) }
            onDelete={ onDelete }
            extraItems={ extras }
        />
        <UpdateDescriptionModal
            isOpen={updateDescriptionOpen}
            onClose={() => setUpdateDescriptionOpen(false)}
            run={run} />
    </>)
}

type UpdateDescriptionModalProps = {
    isOpen: boolean,
    onClose(): void,
    run: Run,
 }

 export function UpdateDescriptionModal({ isOpen, onClose, run} : UpdateDescriptionModalProps) {
    const [ value, setValue ] = useState(run.description)
    const [ updating, setUpdating ] = useState(false)
    const dispatch = useDispatch()
    const thunkDispatch = useDispatch<RunsDispatch>()

    return (<Modal variant="small"
                   title="UpdateDescription"
                   isOpen={isOpen}
                   onClose={onClose}>
       <TextInput
                value={ value }
                type="text"
                id="description"
                name="description"
                onChange={setValue}
                isReadOnly={updating}
            />
            <Button variant="primary" onClick={() => {
                setUpdating(true)
                thunkDispatch(updateDescription(run.id, run.testid, value)).catch(
                    e => {
                        setValue(run.description)
                        dispatch(alertAction("RUN_UPDATE", "Failed to update description for run ID " + run.id, e))
                    }
                ).finally(() => {
                    setUpdating(false)
                    onClose();
                })
            }}>Save</Button>
            <Button variant="secondary" onClick={() => {
                setValue(run.description)
                setUpdating(false)
                onClose();
            }}>Cancel</Button>
    </Modal>)
 }

 export const RunTags = (tags: any) => {
    if (!tags || tags === "") {
        return null;
    }
    return Object.entries(tags).map(([key, tag]: any[]) => (
        <Tooltip content={ key }>
            <Chip key={ tag } isReadOnly>{ tag }</Chip>
        </Tooltip>))
 }