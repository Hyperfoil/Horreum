import React, { useState, useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import {
    ActionGroup,
    Button,
    Dropdown,
    DropdownItem,
    KebabToggle,
    ExpandableSection,
    Form,
    FormGroup,
    Modal,
    TextArea,
    Switch,
} from '@patternfly/react-core';
import {
    CheckIcon
} from '@patternfly/react-icons'
import { NavLink } from 'react-router-dom'
import * as api from './api'
import { Change } from './types'
import { alertAction } from '../../alerts'
import { formatDateTime } from '../../utils'
import { Column, UseSortByColumnOptions } from 'react-table';
import Table from '../../components/Table';
import { isTesterSelector } from '../../auth'


type ChangeMenuProps = {
    change: Change,
    onDelete(id: number): void,
    onUpdate(change: Change): void,
}

const ChangeMenu = ({ change, onDelete, onUpdate }: ChangeMenuProps) => {
    const [open, setOpen] = useState(false)
    const [modalChange, setModalChange] = useState<Change>()
    return (<>
        <Dropdown
            toggle={<KebabToggle onToggle={() => setOpen(!open)} />}
            isOpen={ open }
            isPlain
            dropdownItems={[
                <DropdownItem
                    key="confirm"
                    isDisabled={ change.confirmed }
                    onClick={ () => {
                        setOpen(false)
                        setModalChange({ ...change, confirmed: true })
                    }}
                >Confirm</DropdownItem>,
                <DropdownItem
                    key="delete"
                    isDisabled={ change.confirmed }
                    onClick={ () => {
                        onDelete(change.id)
                        setOpen(false)
                    }}
                >Delete</DropdownItem>,
                <DropdownItem
                    key="update"
                    onClick={ () => {
                        setOpen(false)
                        setModalChange(change)
                    }}
                >Edit</DropdownItem>
            ]} />
        <ChangeModal
            change={ modalChange }
            isOpen={ !!modalChange }
            onClose={ () => setModalChange(undefined) }
            onUpdate={ onUpdate }/>
    </>)
}

type C = Column<Change> & UseSortByColumnOptions<Change>

type ChangeModalProps = {
    change?: Change,
    isOpen: boolean,
    onClose(): void,
    onUpdate(change: Change): void
}

const ChangeModal = ({change, isOpen, onClose, onUpdate }: ChangeModalProps) => {
    const [description, setDescription] = useState(change?.description)
    const [confirmed, setConfirmed] = useState(change?.confirmed)
    useEffect(() => {
        setDescription(change?.description)
        setConfirmed(change?.confirmed)
    }, [change])
    return (
        <Modal
            title={ change?.confirmed ? "Confirm change" : "Edit change" }
            isOpen={ isOpen } onClose={ onClose }
        >
            <Form>
                <FormGroup label="Confirmed" fieldId="confirmed">
                    <Switch
                        id="confirmed"
                        isChecked={confirmed}
                        onChange={ setConfirmed }
                        label="Confirmed"
                        labelOff="Not confirmed"
                    />
                </FormGroup>
                <FormGroup label="Description" fieldId="description">
                    <TextArea
                        value={description || ""}
                        type="text"
                        id="description"
                        aria-describedby="description-helper"
                        name="description"
                        onChange={setDescription}
                    />
                </FormGroup>
            </Form>
            <ActionGroup>
                <Button variant="primary" onClick={ () => {
                    if (change) {
                        onUpdate({ ...change, description: description || "", confirmed: !!confirmed })
                    }
                    onClose()
                }}>Save</Button>
                <Button variant="secondary" onClick={ onClose}>Cancel</Button>
            </ActionGroup>
        </Modal>
    )
}

type ChangesProps = {
    varId: number
}

export default ({ varId } : ChangesProps) => {
    const dispatch = useDispatch()
    const [isExpanded, setExpanded] = useState(false)
    const [changes, setChanges] = useState<Change[]>([])
    useEffect(() => {
        api.fetchChanges(varId).then(
            response => setChanges(response),
            error => dispatch(alertAction("DASHBOARD_FETCH", "Failed to fetch dashboard", error))
        )
    }, [varId])
    const isTester = useSelector(isTesterSelector)
    const columns: C[] = [
        {
            Header: "Confirmed",
            accessor: "confirmed",
            Cell: (arg: any) => arg.cell.value ? <CheckIcon /> : ""
        }, {
            Header: "Time",
            id: "timestamp",
            accessor: "dataPoint",
            Cell: (arg: any) => formatDateTime(arg.cell.value.timestamp)
        }, {
            Header:"Run ID",
            id: "runId",
            accessor:"dataPoint",
            Cell: (arg: any) => <NavLink to={ "/run/" + arg.cell.value.runId } >{ arg.cell.value.runId }</NavLink>
        }, {
            Header:"Description",
            accessor:"description",
            Cell: (arg: any) => <div dangerouslySetInnerHTML={{ __html: arg.cell.value}} />
        },
    ]
    if (isTester) {
        columns.push({
            Header: "",
            accessor: "id",
            disableSortBy: true,
            Cell: (arg: any) => (
                <ChangeMenu
                    change={ arg.row.original }
                    onDelete={ changeId => api.deleteChange(changeId).then(
                        _ => setChanges(changes.filter(c => c.id !== changeId)),
                        error => dispatch(alertAction("CHANGE_DELETE", "Failed to delete change " + changeId, error))
                    ) }
                    onUpdate={ change => api.updateChange(change).then(
                        _ => setChanges(changes.map(c => c.id === change.id ? change : c)),
                        error => dispatch(alertAction("CHANGE_UPDATE", "Failed to update change " + change.id, error))
                    ) }
                />
            )
        })
    }
    return (
        <ExpandableSection toggleText={ isExpanded ? "Hide changes" : "Show changes" }
                           onToggle={setExpanded}
                           isExpanded={isExpanded} >
            <Table columns={columns} data={changes} />
        </ExpandableSection>
    )
}