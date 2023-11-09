import { useState, useEffect } from "react"
import { useDispatch } from "react-redux"
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
    Tab,
    Tabs,
    TextArea,
    Switch,
} from "@patternfly/react-core"
import { CheckIcon } from "@patternfly/react-icons"
import { NavLink } from "react-router-dom"
import {alertingApi, Change, FingerprintValue, Variable} from "../../api"
import { alertAction } from "../../alerts"
import { fingerprintToString, formatDateTime } from "../../utils"
import { Column, UseSortByColumnOptions } from "react-table"
import Table from "../../components/Table"
import { useTester } from "../../auth"

type ChangeMenuProps = {
    change: Change
    onDelete(id: number): void
    onUpdate(change: Change): void
}

const ChangeMenu = ({ change, onDelete, onUpdate }: ChangeMenuProps) => {
    const [open, setOpen] = useState(false)
    const [modalChange, setModalChange] = useState<Change>()
    return (
        <>
            <Dropdown
                toggle={<KebabToggle onToggle={() => setOpen(!open)} />}
                isOpen={open}
                isPlain
                dropdownItems={[
                    <DropdownItem
                        key="confirm"
                        isDisabled={change.confirmed}
                        onClick={() => {
                            setOpen(false)
                            setModalChange({ ...change, confirmed: true })
                        }}
                    >
                        Confirm
                    </DropdownItem>,
                    <DropdownItem
                        key="delete"
                        isDisabled={change.confirmed}
                        onClick={() => {
                            onDelete(change.id)
                            setOpen(false)
                        }}
                    >
                        Delete
                    </DropdownItem>,
                    <DropdownItem
                        key="update"
                        onClick={() => {
                            setOpen(false)
                            setModalChange(change)
                        }}
                    >
                        Edit
                    </DropdownItem>,
                ]}
            />
            <ChangeModal
                change={modalChange}
                isOpen={!!modalChange}
                onClose={() => setModalChange(undefined)}
                onUpdate={onUpdate}
            />
        </>
    )
}

type C = Column<Change> & UseSortByColumnOptions<Change>

type ChangeModalProps = {
    change?: Change
    isOpen: boolean
    onClose(): void
    onUpdate(change: Change): void
}

const ChangeModal = ({ change, isOpen, onClose, onUpdate }: ChangeModalProps) => {
    const [description, setDescription] = useState(change?.description)
    const [confirmed, setConfirmed] = useState(change?.confirmed)
    useEffect(() => {
        setDescription(change?.description)
        setConfirmed(change?.confirmed)
    }, [change])
    return (
        <Modal title={change?.confirmed ? "Confirm change" : "Edit change"} isOpen={isOpen} onClose={onClose}>
            <Form>
                <FormGroup label="Confirmed" fieldId="confirmed">
                    <Switch
                        id="confirmed"
                        isChecked={confirmed}
                        onChange={setConfirmed}
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
                <Button
                    variant="primary"
                    onClick={() => {
                        if (change) {
                            onUpdate({ ...change, description: description || "", confirmed: !!confirmed })
                        }
                        onClose()
                    }}
                >
                    Save
                </Button>
                <Button variant="secondary" onClick={onClose}>
                    Cancel
                </Button>
            </ActionGroup>
        </Modal>
    )
}

type ChangesProps = {
    varId: number
    fingerprint: FingerprintValue | undefined
    testOwner?: string
    selectedChangeId?: number
}

export const ChangeTable = ({ varId, fingerprint, testOwner, selectedChangeId }: ChangesProps) => {
    const dispatch = useDispatch()
    const [changes, setChanges] = useState<Change[]>([])
    useEffect(() => {
        alertingApi.changes(varId, fingerprintToString(fingerprint)).then(
            response => setChanges(response),
            error => dispatch(alertAction("DASHBOARD_FETCH", "Failed to fetch dashboard", error))
        )
    }, [varId, dispatch])
    const isTester = useTester(testOwner)
    const columns: C[] = [
        {
            Header: "Confirmed",
            accessor: "confirmed",
            Cell: (arg: any) => (arg.cell.value ? <CheckIcon id={"change_" + arg.row.original.id} /> : ""),
        },
        {
            Header: "Time",
            accessor: "timestamp",
            sortType: "datetime",
            Cell: (arg: any) => formatDateTime(arg.cell.value),
        },
        {
            Header: "Dataset",
            accessor: "dataset",
            Cell: (arg: any) => {
                const dataset = arg.cell.value
                if (!dataset) return "<unknown>"
                return (
                    <NavLink to={`/run/${dataset.runId}#dataset${dataset.ordinal}`}>
                        {dataset.runId}/{dataset.ordinal + 1}
                    </NavLink>
                )
            },
        },
        {
            Header: "Description",
            accessor: "description",
            Cell: (arg: any) => <div dangerouslySetInnerHTML={{ __html: arg.cell.value }} />,
        },
    ]
    if (isTester) {
        columns.push({
            Header: "",
            accessor: "id",
            disableSortBy: true,
            Cell: (arg: any) => {
                return (
                    <ChangeMenu
                        change={arg.row.original}
                        onDelete={changeId =>
                            alertingApi.deleteChange(changeId).then(
                                _ => setChanges(changes.filter(c => c.id !== changeId)),
                                error =>
                                    dispatch(alertAction("CHANGE_DELETE", "Failed to delete change " + changeId, error))
                            )
                        }
                        onUpdate={change =>
                            alertingApi.updateChange(change.id, change).then(
                                _ => setChanges(changes.map(c => (c.id === change.id ? change : c))),
                                error =>
                                    dispatch(
                                        alertAction("CHANGE_UPDATE", "Failed to update change " + change.id, error)
                                    )
                            )
                        }
                    />
                )
            },
        })
    }
    // TODO: this doesn't work, table won't get updated when selected changes
    const selected = { [changes.findIndex(c => c.id === selectedChangeId)]: true }
    return <Table columns={columns} data={changes} selected={selected} />
}

type ChangesTabsProps = {
    variables: Variable[]
    fingerprint: FingerprintValue | undefined
    testOwner?: string
    selectedChangeId?: number
    selectedVariableId?: number
}

export const ChangesTabs = ({
    variables,
    fingerprint,
    testOwner,
    selectedChangeId,
    selectedVariableId,
}: ChangesTabsProps) => {
    const [isExpanded, setExpanded] = useState(false)
    const [activeTab, setActiveTab] = useState<number | string>(0)
    useEffect(() => {
        const index = variables.findIndex(v => v.id === selectedVariableId)
        if (index >= 0) {
            setExpanded(true)
            setActiveTab(index)
        }
    }, [selectedVariableId, variables])
    const name = variables[0].group || variables[0].name
    return (
        <ExpandableSection
            toggleText={isExpanded ? "Hide changes in " + name : "Show changes in " + name}
            onToggle={setExpanded}
            isExpanded={isExpanded}
        >
            <Tabs
                activeKey={activeTab}
                onSelect={(e, index) => {
                    setActiveTab(index)
                }}
            >
                {variables.map((v, index) => (
                    <Tab key={v.name} eventKey={index} title={v.name}>
                        <ChangeTable
                            varId={v.id}
                            fingerprint={fingerprint}
                            testOwner={testOwner}
                            selectedChangeId={selectedChangeId}
                        />
                    </Tab>
                ))}
            </Tabs>
        </ExpandableSection>
    )
}
