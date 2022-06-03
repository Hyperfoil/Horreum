import { useEffect, useState } from "react"

import { useDispatch } from "react-redux"
import { UseSortByColumnOptions } from "react-table"
import { Bullseye, Button, Spinner, Toolbar, ToolbarContent, ToolbarItem } from "@patternfly/react-core"
import { OutlinedTimesCircleIcon, PlusIcon } from "@patternfly/react-icons"

import Api, { AllowedHookPrefix } from "../../api"

import { alertAction } from "../../alerts"

import Table from "../../components/Table"
import AddPrefixModal from "./AddPrefixModal"
import { Column } from "react-table"

type C = Column<AllowedHookPrefix> & UseSortByColumnOptions<AllowedHookPrefix>

function PrefixList() {
    const dispatch = useDispatch()
    const [prefixes, setPrefixes] = useState<AllowedHookPrefix[]>()
    useEffect(() => {
        setPrefixes(undefined)
        Api.hookServiceAllowedPrefixes().then(setPrefixes, e =>
            dispatch(alertAction("FETCH_HOOK_PREFIXES", "Failed to fetch allowed hook prefixes", e))
        )
    }, [dispatch])
    const columns: C[] = [
        {
            Header: "Prefix",
            accessor: "prefix",
            Cell: (arg: any) => {
                const {
                    cell: { value },
                } = arg
                return value === "" ? <span style={{ color: "#888" }}>&lt;empty&gt;</span> : value
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
                            if (prefixes) {
                                setPrefixes(prefixes.filter(p => p.id !== value))
                            }
                            Api.hookServiceDeletePrefix(value).catch(e =>
                                dispatch(alertAction("REMOVE_HOOK_PREFIX", "Failed to remove hook prefix.", e))
                            )
                        }}
                    >
                        <OutlinedTimesCircleIcon />
                    </Button>
                )
            },
        },
    ]
    const [isOpen, setOpen] = useState(false)

    return (
        <>
            <AddPrefixModal
                isOpen={isOpen}
                onClose={() => setOpen(false)}
                onSubmit={prefix =>
                    Api.hookServiceAddPrefix(prefix).then(
                        p => setPrefixes([...(prefixes || []), p]),
                        e => dispatch(alertAction("ADD_HOOK_PREFIX", "Failed to add hook prefix.", e))
                    )
                }
            />
            <Toolbar
                className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md"
                style={{ justifyContent: "space-between" }}
            >
                <ToolbarContent>
                    <ToolbarItem aria-label="info">
                        <Button onClick={() => setOpen(true)}>
                            <PlusIcon /> Add Prefix
                        </Button>
                    </ToolbarItem>
                </ToolbarContent>
            </Toolbar>
            {!prefixes && (
                <Bullseye>
                    <Spinner size="xl" />
                </Bullseye>
            )}
            {prefixes && <Table columns={columns} data={prefixes} />}
        </>
    )
}

export default PrefixList
