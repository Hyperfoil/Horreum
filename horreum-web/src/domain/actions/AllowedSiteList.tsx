import {useContext, useEffect, useState} from "react"

import { UseSortByColumnOptions } from "react-table"
import { Bullseye, Button, Spinner, Toolbar, ToolbarContent, ToolbarItem } from "@patternfly/react-core"

import { addSite, AllowedSite, deleteSite, getAllowedSites} from "../../api"


import Table from "../../components/Table"
import AddAllowedSiteModal from "./AddAllowedSiteModal"
import { Column } from "react-table"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";

type C = Column<AllowedSite> & UseSortByColumnOptions<AllowedSite>

function AllowedSiteList() {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [prefixes, setPrefixes] = useState<AllowedSite[]>()
    useEffect(() => {
        setPrefixes(undefined)
        getAllowedSites(alerting).then(setPrefixes)
    }, [])
    const columns: C[] = [
        {
            Header: "Site prefix",
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
                    <div style={{ textAlign: "right" }}>
                        <Button
                            variant="danger"
                            onClick={() => {
                                if (prefixes) {
                                    setPrefixes(prefixes.filter(p => p.id !== value))
                                }
                                return deleteSite(value, alerting)
                            }}
                        >
                            Delete
                        </Button>
                    </div>
                )
            },
        },
    ]
    const [isOpen, setOpen] = useState(false)

    return (
        <>
            <AddAllowedSiteModal
                isOpen={isOpen}
                onClose={() => setOpen(false)}
                onSubmit={prefix => addSite(prefix, alerting)}
            />
            <Toolbar
                className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md"
                style={{ justifyContent: "space-between" }}
            >
                <ToolbarContent>
                    <ToolbarItem aria-label="info">
                        <Button onClick={() => setOpen(true)}>Add Allowed Site</Button>
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

export default AllowedSiteList
