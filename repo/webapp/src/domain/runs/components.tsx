import React from 'react'
import {
    DropdownItem,
    Tooltip,
} from '@patternfly/react-core';
import moment from 'moment'
import { useDispatch } from 'react-redux'

import { Run, RunsDispatch } from './reducers';
import { resetToken, dropToken, updateAccess, trash } from './actions';
import ActionMenu, { DropdownItemProvider } from '../../components/ActionMenu';
import { alertAction } from '../../alerts';
import { formatDateTime, toEpochMillis } from '../../utils'


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
    return (
     <ActionMenu id={run.id}
                 owner={ run.owner }
                 access={ run.access }
                 token={ run.token || undefined }
                 tokenToLink={ (id, token) => "/run/" + id + "?token=" + token }
                 onTokenReset={ id => dispatch(resetToken(id, run.testid)) }
                 onTokenDrop={ id => dispatch(dropToken(id, run.testid)) }
                 onAccessUpdate={ (id, owner, access) => dispatch(updateAccess(id, run.testid, owner, access)) }
                 onDelete={ onDelete }
                 extraItems={ extras }/>
    )
}