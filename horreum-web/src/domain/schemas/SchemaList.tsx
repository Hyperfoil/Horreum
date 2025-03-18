import React, {useMemo, useEffect, useState, useContext} from "react"
import {
    PageSection,
    Toolbar,
    ToolbarContent,
    ToolbarItem,
    ToolbarGroup
} from "@patternfly/react-core"
import {NavLink, useNavigate} from "react-router-dom"

import {useTester, teamsSelector, teamToName} from "../../auth"
import {noop} from "../../utils"

import ActionMenu, {useChangeAccess, useDelete} from "../../components/ActionMenu"
import ButtonLink from "../../components/ButtonLink"
import {CellProps, Column} from "react-table"
import {Access, SortDirection, Schema, schemaApi, SchemaExport} from "../../api"
import TeamSelect, {ONLY_MY_OWN, Team, createTeam} from "../../components/TeamSelect";
import AccessIcon from "../../components/AccessIcon"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import {useSelector} from "react-redux";
import ImportButton from "../../components/ImportButton";
import CustomTable from "../../components/CustomTable"
import FilterSearchInput from "../../components/FilterSearchInput";

type C = CellProps<Schema>

export default function SchemaList() {
    document.title = "Schemas | Horreum"
    const params = new URLSearchParams(location.search)
    const navigate = useNavigate()
    const {alerting} = useContext(AppContext) as AppContextType;
    const teams = useSelector(teamsSelector)

    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [direction] = useState<SortDirection>('Ascending')
    const pagination = useMemo(() => ({page, perPage, direction}), [page, perPage, direction])
    const [schemas, setSchemas] = useState<Schema[]>([])
    const [schemaCount, setSchemaCount] = useState(0)
    const [loading, setLoading] = useState(false)
    const [reloadCounter, setReloadCounter] = useState(0)
    const rolesFilterFromQuery = params.get("filter")
    const [rolesFilter, setRolesFilter] = useState<Team>(rolesFilterFromQuery !== null ? createTeam(rolesFilterFromQuery) : ONLY_MY_OWN)
    const [nameFilter, setNameFilter] = useState<string>("")

    const isTester = useTester()

    const removeSchema = (id: number) => {
        if (schemaCount > 0) {
            setSchemas(schemas?.filter(schema => schema.id !== id) || [])
            const newCount = (schemaCount == 0) ? 0 : schemaCount - 1
            setSchemaCount(newCount)
        }
    }

    const reloadSchemas = () => {
        setLoading(true)
        schemaApi
            .list(pagination.perPage, pagination.page - 1, "", SortDirection.Ascending, rolesFilter.key, nameFilter)
            .then((result) => {
                setSchemas(result.schemas)
                setSchemaCount(result.count)
            })
            .catch(error => alerting.dispatchError(error, "FETCH_SCHEMA", "Failed to fetch schemas"))
            .finally(() => setLoading(false))
    }

    useEffect(() => {
        reloadSchemas()
    }, [pagination, reloadCounter, teams, rolesFilter, nameFilter])

    useEffect(() => {
        // set query param in the url
        let query = ""
        if (rolesFilter.key !== ONLY_MY_OWN.key) {
            query = "?filter=" + rolesFilter.key
        }
        navigate(location.pathname + query)
    }, [rolesFilter])

    const columns: Column<Schema>[] = useMemo(
        () => [
            {
                Header: "Name",
                id: "name",
                accessor: "name",
                Cell: (arg: C) => {
                    return <NavLink to={"/schema/" + arg.row.original.id}>{arg.cell.value}</NavLink>
                },
            },
            {
                Header: "URI",
                id: "uri",
                accessor: "uri",
            },
            {
                Header: "Description",
                id: "description",
                accessor: "description",
            },
            {
                Header: "Owner",
                id: "owner",
                accessor: (row: Schema) => ({
                    owner: row.owner,
                    access: row.access,
                }),
                Cell: (arg: C) => (
                    <>
                        {teamToName(arg.cell.value.owner)}
                        <span style={{ marginLeft: '8px' }}>
                            <AccessIcon access={arg.cell.value.access} showText={false} />
                        </span>
                    </>
                ),
            },
            {
                Header: "Actions",
                accessor: "id",
                disableSortBy: true,
                Cell: arg => {
                    const changeAccess = useChangeAccess({
                        onAccessUpdate: (id, owner, access) => {
                            return schemaApi.updateAccess(id, owner, access).then(
                                () => noop(),
                                error => alerting.dispatchError(error, "SCHEMA_UPDATE", "Failed to update schema access.")
                            ).then(() => reloadSchemas)
                        },
                    })
                    const del = useDelete({
                        onDelete: id => {
                            return schemaApi._delete(id)
                                .then(() => id,
                                    error => alerting.dispatchError(error, "SCHEMA_DELETE", "Failed to delete schema " + id)
                                ).then(id => removeSchema(id))
                                .catch(noop)
                        },
                    })
                    return (
                        <ActionMenu
                            id={arg.cell.value}
                            owner={arg.row.original.owner}
                            access={arg.row.original.access as Access}
                            description={"schema " + arg.row.original.name + " (" + arg.row.original.uri + ")"}
                            items={[changeAccess, del]}
                        />
                    )
                },
            },
        ],
        [schemas]
    )

    return (
        <PageSection>
            <Toolbar>
                <ToolbarContent>
                    {isTester && (
                        <ToolbarItem>
                            <TeamSelect
                                includeGeneral={true}
                                selection={rolesFilter}
                                onSelect={selection => {
                                    setRolesFilter(selection)
                                }}
                            />
                        </ToolbarItem>
                    )}
                    <ToolbarItem>
                        <FilterSearchInput
                            placeholder="Filter by name"
                            onSearchBy={setNameFilter}
                            onClearBy={() => setNameFilter("")}
                        />
                    </ToolbarItem>
                    {isTester && (
                        <ToolbarGroup variant="button-group" align={{ default: 'alignRight' }}>
                            <ToolbarItem>
                                <ImportButton
                                    label="Import schema"
                                    onLoad={config => {
                                        const overridden = schemas.find(s => s.id === config.id)
                                        return overridden ? (
                                            <>
                                                This configuration is going to override schema {overridden.name} ({overridden.id})
                                                {config?.name !== overridden.name && ` using new name ${config?.name}`}.<br />
                                                <br />
                                                Do you really want to proceed?
                                            </>
                                        ) : null
                                    }}
                                    onImport={config => {
                                        return config.id > 0 ? schemaApi.updateSchema(config as SchemaExport) : schemaApi.importSchema(config as SchemaExport)
                                    }}
                                    onImported={ reloadSchemas }
                                />
                            </ToolbarItem>
                            <ToolbarItem>
                                <ButtonLink style={{marginRight: "16px", width: "100pt"}} to="/schema/_new">New
                                    Schema</ButtonLink>
                            </ToolbarItem>
                        </ToolbarGroup>
                    )}
                </ToolbarContent>
            </Toolbar>
            <CustomTable<Schema> columns={columns}
                           data={schemas || []}
                           sortBy={[{id: "name", desc: false}]}
                           isLoading={loading}
                           pagination={{
                               bottom: true,
                               count: schemaCount || 0,
                               perPage: perPage,
                               page: page,
                               onSetPage: (e, p) => setPage(p),
                               onPerPageSelect: (e, pp) => setPerPage(pp)
                           }}
                           cellModifier="wrap"
            />
        </PageSection>
    )
}
