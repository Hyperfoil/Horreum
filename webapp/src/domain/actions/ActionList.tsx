import { useEffect, useMemo, useState } from "react"
import { useDispatch, useSelector } from "react-redux"

import { Button, Hint, HintBody, Switch, Toolbar, ToolbarContent, ToolbarItem } from "@patternfly/react-core"

import { allActions, addAction, removeAction } from "./actions"
import * as selectors from "./selectors"
import { isAdminSelector } from "../../auth"
import { noop } from "../../utils"

import { fetchSummary } from "../tests/actions"

import Table from "../../components/Table"
import AddActionModal from "./AddActionModal"
import { Column } from "react-table"
import { ActionsDispatch } from "./reducers"
import { Action } from "../../api"
import ActionLogModal from "../tests/ActionLogModal"

export default function ActionList() {
    const [logOpen, setLogOpen] = useState(false)
    const dispatch = useDispatch<ActionsDispatch>()
    useEffect(() => {
        dispatch(fetchSummary()).catch(noop)
    }, [dispatch])
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
                                onClick={() => {
                                    dispatch(removeAction(value)).catch(noop)
                                }}
                            >
                                Delete
                            </Button>
                        </div>
                    )
                },
            },
        ],
        [dispatch]
    )
    const [isOpen, setOpen] = useState(false)
    const list = useSelector(selectors.all)
    const isAdmin = useSelector(isAdminSelector)
    useEffect(() => {
        if (isAdmin) {
            dispatch(allActions()).catch(noop)
        }
    }, [dispatch, isAdmin])
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
                onSubmit={h => dispatch(addAction(h)).catch(noop)}
            />
            <Table columns={columns} data={list || []} />
        </>
    )
}
