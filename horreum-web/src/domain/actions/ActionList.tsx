import {useContext, useEffect, useMemo, useState} from "react"
import { useSelector } from "react-redux"

import { Button, Hint, HintBody, PageSection, Switch, Toolbar, ToolbarContent, ToolbarItem } from "@patternfly/react-core"

import {allActions, addGlobalAction, deleteGlobalAction} from "../../api"
import { isAdminSelector } from "../../auth"

import AddActionModal from "./AddActionModal"
import {Action} from "../../api"
import ActionLogModal from "../tests/ActionLogModal"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import CustomTable from "../../components/CustomTable"
import { ColumnDef, createColumnHelper } from "@tanstack/react-table"

const columnHelper = createColumnHelper<Action>()

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

    const columns: ColumnDef<Action, any>[] = useMemo(
        () => [
            columnHelper.accessor("event", {
                header: "EventType",
            }),
            columnHelper.accessor("type", {
                header: "Action Type",
            }),
            columnHelper.accessor("runAlways", {
                header: "Run Always",
                cell: ({ getValue }) => <Switch isChecked={getValue()} label="Enabled" onChange={(e, _) => e.preventDefault()} />,
            }),
            columnHelper.accessor("config", {
                header: "Configuration",
                cell: ({ row }) => {
                    const config: any = row.original.config
                    switch (row.original.type) {
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
            }),
            columnHelper.display({
                id: "delete",
                cell: ({ row }) =>
                    <div style={{ textAlign: "right" }}>
                        <Button
                            variant="danger"
                            onClick={() => deleteGlobalAction(row.original.id, alerting).then(_ => fetchAllActions())}
                        >
                            Delete
                        </Button>
                    </div>
                }),
            ],
        []
    )

    return (
        <>
            <PageSection>
                <Hint>
                    <HintBody>
                        These Actions are global actions. For individual test actions, please go to Test configuration.
                    </HintBody>
                </Hint>
            </PageSection>
            <Toolbar
                className="pf-v6-l-toolbar pf-v6-u-justify-content-space-between pf-v6-u-mx-xl pf-v6-u-my-md"
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
