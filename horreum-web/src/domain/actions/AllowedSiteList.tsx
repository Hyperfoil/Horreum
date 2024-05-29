import {useContext, useEffect, useState} from "react"

import { UseSortByColumnOptions } from "react-table"
import { Bullseye, Button, Spinner, Toolbar, ToolbarContent, ToolbarItem } from "@patternfly/react-core"

import { addSite, AllowedSite, deleteSite, getAllowedSites} from "../../api"


import AddAllowedSiteModal from "./AddAllowedSiteModal"
import { Column } from "react-table"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import CustomTable, { StickyProps } from "../../components/CustomTable"

type C = Column<AllowedSite> & UseSortByColumnOptions<AllowedSite> & StickyProps

function AllowedSiteList() {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [prefixes, setPrefixes] = useState<AllowedSite[]>()

    const fetchAllowedSites = () => {
        setPrefixes(undefined)
        getAllowedSites(alerting).then(setPrefixes)
    }

    useEffect(() => {
        fetchAllowedSites()
    }, [])
    const columns: C[] = [
        {
            Header: "Site prefix",
            id: "prefix",
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
                            onClick={() => {
                                if (prefixes) {
                                    setPrefixes(prefixes.filter(p => p.id !== value))
                                }
                                return deleteSite(value, alerting).then(_ => fetchAllowedSites())
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
                onSubmit={prefix => addSite(prefix, alerting).then((_) => fetchAllowedSites())}
            />
            <Toolbar
                className="pf-v5-l-toolbar pf-v5-u-justify-content-space-between pf-v5-u-mx-xl pf-v5-u-my-md"
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
            {prefixes && <CustomTable<AllowedSite> columns={columns} data={prefixes} />}
        </>
    )
}

export default AllowedSiteList
