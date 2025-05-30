import {useContext, useEffect, useMemo, useState} from "react"
import { useSelector } from "react-redux"

import { Button, Hint, HintBody, Switch, Toolbar, ToolbarContent, ToolbarItem } from "@patternfly/react-core"

import {allActions, addGlobalAction, deleteGlobalAction} from "../../api"
import { isAdminSelector } from "../../auth"

import AddActionModal from "./AddActionModal"
import { Column } from "react-table"
import {Action} from "../../api"
import ActionLogModal from "../tests/ActionLogModal"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import CustomTable from "../../components/CustomTable"

export default function ActionList() {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [logOpen, setLogOpen] = useState(false)
    const [isOpen, setOpen] = useState(false)
    const [actions, setActions] = useState<Action[]>([])
    const isAdmin = useSelector(isAdminSelector)

    const fetchAllActions = () => {
        allActions(alerting).then(setActions)
    }

    useEffect(() => {
        if (isAdmin) {
            fetchAllActions()
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
                id: "event",
                accessor: "event",
            },
            {
                Header: "Action type",
                id: "type",
                accessor: "type",
            },
            {
                Header: "Run always",
                id: "runAlways",
                accessor: "runAlways",
                Cell: (arg: any) => {
                    return (
                        <Switch
                            isChecked={arg.cell.value}
                            label="Enabled"
                            labelOff="Disabled"
                            onChange={(e, _) => e.preventDefault()}
                        />
                    )
                },
            },
            {
                Header: "Configuration",
                id: "config",
                accessor: "config",
                Cell: (arg: any) => {
                    const config = arg.cell.value
                    switch (arg.row.original.type) {
                        case "http":
                            return config.url
                        case "github":
                            return config.issueURL || `${config.owner}/${config.repo}/${config.issue}`
                        case "slack":
                            return config.channel
                        default:
                            return "unknown"
                    }
                },
            },
            {
                Header: "",
                id: "id",
                accessor: "id",
                disableSortBy: true,
                isStickyColumn: true,
                hasLeftBorder: false,
                Cell: (arg: any) => {
                    const {
                        cell: { value },
                    } = arg
                    return (
                        <div style={{ textAlign: "right" }}>
                            <Button
                                variant="danger"
                                onClick={() => deleteGlobalAction(value, alerting).then(_ => fetchAllActions())}
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
                className="pf-v5-l-toolbar pf-v5-u-justify-content-space-between pf-v5-u-mx-xl pf-v5-u-my-md"
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
                onSubmit={action => addGlobalAction(action, alerting).then(_ => fetchAllActions())}
            />
            <CustomTable<Action> columns={columns} data={actions || []} />
        </>
    )
}
