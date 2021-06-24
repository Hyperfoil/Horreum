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
import { useDispatch } from 'react-redux'

import { Run, RunsDispatch } from './reducers';
import { resetToken, dropToken, updateAccess, trash, updateDescription } from './actions';
import ActionMenu, {
    ActionMenuProps,
    MenuItem,
    useShareLink,
    useChangeAccess,
    useDelete,
} from '../../components/ActionMenu';
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


function useRestore(run: Run): MenuItem<Run> {
    const dispatch = useDispatch()
    const thunkDispatch = useDispatch<RunsDispatch>()
    return [ (props: ActionMenuProps, isOwner: boolean, close: () => void, run: Run) => {
        return ({
            item:
                <DropdownItem key="restore"
                    onClick={() => {
                        close()
                        thunkDispatch(trash(run.id, run.testid, false)).catch(
                            e => dispatch(alertAction("RUN_TRASH", "Failed to restore run ID " + run.id, e))
                        )
                    }}
                >Restore</DropdownItem>,
            modal:
                <></>
        })
    }, run]
}

function useUpdateDescription(run: Run): MenuItem<Run> {
    const [updateDescriptionOpen, setUpdateDescriptionOpen] = useState(false)
    return [ (props: ActionMenuProps, isOwner: boolean, close: () => void, run: Run) => {
        return ({
            item:
                <DropdownItem key="updateDescription"
                    onClick={() => {
                        close()
                        setUpdateDescriptionOpen(true)
                    }}
                >Edit description</DropdownItem>,
            modal:
                <UpdateDescriptionModal
                    isOpen={updateDescriptionOpen}
                    onClose={() => setUpdateDescriptionOpen(false)}
                    run={run} />
        })
    }, run ]
}

export function Menu(run: Run) {
    const dispatch = useDispatch()
    const thunkDispatch = useDispatch<RunsDispatch>()

    const shareLink = useShareLink({
        token: run.token || undefined,
        tokenToLink: (id, token) => "/run/" + id + "?token=" + token,
        onTokenReset: id => dispatch(resetToken(id, run.testid)),
        onTokenDrop: id => dispatch(dropToken(id, run.testid)),
    })
    const changeAccess = useChangeAccess({
        onAccessUpdate: (id, owner, access) => thunkDispatch(updateAccess(id, run.testid, owner, access)).catch(
            e => dispatch(alertAction("UPDATE_RUN_ACCESS", "Failed to update run access", e))
        ),
    })
    const del = useDelete({
        onDelete: id => thunkDispatch(trash(id, run.testid)).catch(
            e => dispatch(alertAction("RUN_TRASH", "Failed to trash run ID " + id, e))
        )
    })
    const restore = useRestore(run)
    let menuItems: MenuItem<any>[] = [ shareLink, changeAccess ]
    menuItems.push(run.trashed ? restore : del)

    const isTester = useTester(run.owner)
    const updateDescription = useUpdateDescription(run)
    if (isTester) {
        menuItems.push(updateDescription)
    }

    return (
        <ActionMenu
            id={run.id}
            description={ "run " + run.id }
            owner={ run.owner }
            access={ run.access }
            items={ menuItems }
        />
    )
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