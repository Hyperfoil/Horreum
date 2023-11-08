import {useContext, useEffect, useMemo, useState} from "react"
import { useSelector } from "react-redux"

import { Button, Hint, HintBody, Switch, Toolbar, ToolbarContent, ToolbarItem } from "@patternfly/react-core"

import { allActions, addAction, removeAction } from "../../api"
import { isAdminSelector } from "../../auth"

import Table from "../../components/Table"
import AddActionModal from "./AddActionModal"
import { Column } from "react-table"
import {Action} from "../../api"
import ActionLogModal from "../tests/ActionLogModal"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";

export default function ActionList() {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [logOpen, setLogOpen] = useState(false)
    const [isOpen, setOpen] = useState(false)
    const [actions, setActions] = useState<Action[]>([])
    const isAdmin = useSelector(isAdminSelector)

    useEffect(() => {
        if (isAdmin) {
            allActions(alerting).then(setActions)
        }
    }, [isAdmin])
/*
    const removeAction = (id: number, alerting: AlertContextType) => {
        return (dispatch: Dispatch<DeleteAction >) =>
            actionApi._delete(id).then(
                _ => dispatch(removed(id)),
                error => alerting.dispatchError(error, "REMOVE_ACTION", "Failed to remove action")
            )
    }
*/

    const columns: Column<Action>[] = useMemo(
        () => [
            {
                Header: "Event type",
                accessor: "event",
            },
            {
                Header: "Action type",
                accessor: "type",
            },
            {
                Header: "Run always",
                accessor: "runAlways",
                Cell: (arg: any) => {
                    return (
                        <Switch
                            isChecked={arg.cell.value}
                            label="Enabled"
                            labelOff="Disabled"
                            onChange={(_, e) => e.preventDefault()}
                        />
                    )
                },
            },
            {
                Header: "Configuration",
                accessor: "config",
                Cell: (arg: any) => {
                    const config = arg.cell.value
                    switch (arg.row.original.type) {
                        case "http":
                            return config.url
                        case "github":
                            return config.issueURL || `${config.owner}/${config.repo}/${config.issue}`
                        default:
                            return "unknown"
                    }
                },
            },
            {
                Header: "",
                accessor: "id",
                disableSortBy: true,
                Cell: (arg: any) => {
                    const {
                        cell: { value },
                    } = arg
                    return (
                        <div style={{ textAlign: "right" }}>
                            <Button
                                variant="danger"
                                onClick={() => removeAction(value, alerting)}
                            >
                                Delete
                            </Button>
                        </div>
                    )
                },
            },
        ], undefined
    )

    return (
        <>
            <Hint>
                <HintBody>
                    These Actions are global actions. For individual test actions, please go to Test configuration.
                </HintBody>
            </Hint>
            <Toolbar
                className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md"
                style={{ justifyContent: "space-between" }}
            >
                <ToolbarContent>
                    <ToolbarItem aria-label="info">
                        <Button variant="primary" onClick={() => setOpen(true)}>
                            Add Action
                        </Button>
                        <Button variant="secondary" onClick={() => setLogOpen(true)}>
                            Show log
                        </Button>
                        <ActionLogModal
                            isOpen={logOpen}
                            onClose={() => setLogOpen(false)}
                            testId={-1}
                            title="Global actions log"
                            emptyMessage="There are no logs for global actions."
                        />
                    </ToolbarItem>
                </ToolbarContent>
            </Toolbar>
            <AddActionModal
                isOpen={isOpen}
                onClose={() => setOpen(false)}
                onSubmit={action => addAction(action, alerting)}
            />
            <Table columns={columns} data={actions || []} />
        </>
    )
}
