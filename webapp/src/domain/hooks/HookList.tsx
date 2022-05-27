import { useEffect, useMemo, useState } from "react"

import { useSelector } from "react-redux"
import { useDispatch } from "react-redux"
import { Banner, Button, Toolbar, ToolbarContent, ToolbarItem } from "@patternfly/react-core"
import { OutlinedTimesCircleIcon, PlusIcon } from "@patternfly/react-icons"

import { allHooks, addHook, removeHook } from "./actions"
import * as selectors from "./selectors"
import { isAdminSelector } from "../../auth"
import { noop } from "../../utils"

import { fetchSummary } from "../tests/actions"

import Table from "../../components/Table"
import AddHookModal from "./AddHookModal"
import { Column } from "react-table"
import { Hook, HooksDispatch } from "./reducers"

export default function HookList() {
    const dispatch = useDispatch<HooksDispatch>()
    useEffect(() => {
        dispatch(fetchSummary()).catch(noop)
    }, [dispatch])
    const columns: Column<Hook>[] = useMemo(
        () => [
            {
                Header: "Url",
                accessor: "url",
            },
            {
                Header: "Event type",
                accessor: "type",
            },
            {
                Header: "Active",
                accessor: "active",
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
                                dispatch(removeHook(value)).catch(noop)
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
            dispatch(allHooks()).catch(noop)
        }
    }, [dispatch, isAdmin])
    return (
        <>
            <Banner variant="info">
                These Webhooks are global webhooks. For individual test webhooks, please configure in the Test
                definition.
            </Banner>
            <Toolbar
                className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md"
                style={{ justifyContent: "space-between" }}
            >
                <ToolbarContent>
                    <ToolbarItem aria-label="info">
                        <Button variant="primary" onClick={() => setOpen(true)}>
                            <PlusIcon /> Add Hook
                        </Button>
                    </ToolbarItem>
                </ToolbarContent>
            </Toolbar>
            <AddHookModal
                isOpen={isOpen}
                onClose={() => setOpen(false)}
                onSubmit={h => dispatch(addHook(h)).catch(noop)}
            />
            <Table columns={columns} data={list || []} />
        </>
    )
}
