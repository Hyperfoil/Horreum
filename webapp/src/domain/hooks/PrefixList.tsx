import React, { useEffect, useState } from "react"

import { useDispatch } from "react-redux"
import { UseSortByColumnOptions } from "react-table"
import {
    Bullseye,
    Button,
    Card,
    CardHeader,
    CardBody,
    Spinner,
    Toolbar,
    ToolbarContent,
    ToolbarItem,
} from "@patternfly/react-core"
import { OutlinedTimesCircleIcon, PlusIcon } from "@patternfly/react-icons"

import { fetchPrefixes, addPrefix, removePrefix } from "./api"

import { alertAction } from "../../alerts"

import Table from "../../components/Table"
import AddPrefixModal from "./AddPrefixModal"
import { Column } from "react-table"
import { AllowedHookPrefix } from "./reducers"

type C = Column<AllowedHookPrefix> & UseSortByColumnOptions<AllowedHookPrefix>

function PrefixList() {
    const dispatch = useDispatch()
    const [prefixes, setPrefixes] = useState<AllowedHookPrefix[]>()
    useEffect(() => {
        setPrefixes(undefined)
        fetchPrefixes().then(setPrefixes, e =>
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
                            removePrefix(value).catch(e =>
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
        <Card>
            <CardHeader>
                <AddPrefixModal
                    isOpen={isOpen}
                    onClose={() => setOpen(false)}
                    onSubmit={prefix =>
                        addPrefix(prefix).then(
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
            </CardHeader>
            <CardBody>
                {!prefixes && (
                    <Bullseye>
                        <Spinner size="xl" />
                    </Bullseye>
                )}
                {prefixes && <Table columns={columns} data={prefixes} />}
            </CardBody>
        </Card>
    )
}

export default PrefixList
