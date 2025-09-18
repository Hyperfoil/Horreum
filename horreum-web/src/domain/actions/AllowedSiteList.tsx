import {useContext, useEffect, useState} from "react"

import { Bullseye, Button, Spinner, Toolbar, ToolbarContent, ToolbarItem } from "@patternfly/react-core"

import { addSite, AllowedSite, deleteSite, getAllowedSites} from "../../api"

import AddAllowedSiteModal from "./AddAllowedSiteModal"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import CustomTable from "../../components/CustomTable"
import { ColumnDef, createColumnHelper } from "@tanstack/react-table"

const columnHelper = createColumnHelper<AllowedSite>()

function AllowedSiteList() {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [isOpen, setOpen] = useState(false)
    const [prefixes, setPrefixes] = useState<AllowedSite[]>()

    const fetchAllowedSites = () => {
        setPrefixes(undefined)
        getAllowedSites(alerting).then(setPrefixes)
    }

    useEffect(() => {
        fetchAllowedSites()
    }, [])

    const columns : ColumnDef<AllowedSite, any>[] = [
            columnHelper.accessor("prefix", {
                header: "Site Prefix",
                cell: ({ row }) => row.original.prefix === "" ? <span style={{ color: "#888" }}>&lt;empty&gt;</span> : row.original.prefix,
            }),
            columnHelper.display({
                header: "",
                id: "id",
                cell: ({ row }) =>
                    <div style={{ textAlign: "right" }}>
                        <Button
                            variant="danger"
                            onClick={() => {
                                if (prefixes) {
                                    setPrefixes(prefixes.filter(p => p.id !== row.original.id))
                                }
                                return deleteSite(row.original.id as number, alerting).then(fetchAllowedSites)
                            }}
                        >
                            Delete
                        </Button>
                    </div>
            }),
        ];

    return (
        <>
            <AddAllowedSiteModal
                isOpen={isOpen}
                onClose={() => setOpen(false)}
                onSubmit={prefix => addSite(prefix, alerting).then((_) => fetchAllowedSites())}
            />
            <Toolbar
                className="pf-v6-l-toolbar pf-v6-u-justify-content-space-between pf-v6-u-mx-xl pf-v6-u-my-md"
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
