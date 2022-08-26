import { useEffect, useMemo, useState } from "react"
import { useDispatch, useSelector } from "react-redux"

import { Banner, Button, Toolbar, ToolbarContent, ToolbarItem } from "@patternfly/react-core"
import { OutlinedTimesCircleIcon, PlusIcon } from "@patternfly/react-icons"

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

export default function ActionList() {
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
                Header: "Active",
                accessor: "active",
            },
            {
                Header: "Configuration",
                accessor: "config",
                Cell: (arg: any) => {
                    switch (arg.row.original.type) {
                        case "http":
                            return arg.cell.value.url
                        case "github":
                            return "some github stuff"
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
                        <Button
                            variant="link"
                            style={{ color: "#a30000" }}
                            onClick={() => {
                                dispatch(removeAction(value)).catch(noop)
                            }}
                        >
                            <OutlinedTimesCircleIcon />
                        </Button>
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
            <Banner variant="info">
                These Actopms are global actions. For individual test actions, please go to Test configuration.
            </Banner>
            <Toolbar
                className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md"
                style={{ justifyContent: "space-between" }}
            >
                <ToolbarContent>
                    <ToolbarItem aria-label="info">
                        <Button variant="primary" onClick={() => setOpen(true)}>
                            <PlusIcon /> Add Action
                        </Button>
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
