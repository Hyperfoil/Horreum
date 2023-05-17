import { useMemo, useEffect, useState } from "react"
import { useSelector } from "react-redux"
import { useDispatch } from "react-redux"
import { Card, CardHeader, CardFooter, CardBody, PageSection, Pagination } from "@patternfly/react-core"
import { NavLink } from "react-router-dom"

import * as actions from "./actions"
import { alertAction } from "../../alerts"
import { useTester, teamsSelector, teamToName } from "../../auth"
import { noop } from "../../utils"
import Table from "../../components/Table"
import AccessIcon from "../../components/AccessIcon"
import ActionMenu, { useShareLink, useChangeAccess, useDelete } from "../../components/ActionMenu"
import ButtonLink from "../../components/ButtonLink"
import { CellProps, Column } from "react-table"
import { SchemaDispatch } from "./reducers"
import Api, { Access, SortDirection, SchemaQueryResult, Schema } from "../../api"
import SchemaImportButton from "./SchemaImportButton"

type C = CellProps<Schema>

export default function AllSchema() {
    document.title = "Schemas | Horreum"
    const dispatch = useDispatch<SchemaDispatch>()

    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [direction] = useState<SortDirection>('Ascending')
    const pagination = useMemo(() => ({ page, perPage, direction }), [page, perPage, direction])
    const [schemas, setSchemas] = useState<SchemaQueryResult>()
    const [loading, setLoading] = useState(false)

    useEffect(() => {
        setLoading(true)
        Api.schemaServiceList(
             'Ascending',
             pagination.perPage,
             pagination.page - 1
             )
            .then(setSchemas)
            .catch(error => dispatch(alertAction("FETCH_SCHEMA", "Failed to fetch schemas", error)))
            .finally(() => setLoading(false))
    }, [pagination,  dispatch])

    const columns: Column<Schema>[] = useMemo(
        () => [
            {
                Header: "Name",
                accessor: "name",
                disableSortBy: false,
                Cell: (arg: C) => {
                    return <NavLink to={"/schema/" + arg.row.original.id}>{arg.cell.value}</NavLink>
                },
            },
            {
                Header: "URI",
                accessor: "uri",
            },
            {
                Header: "Description",
                accessor: "description",
            },
            {
                Header: "Owner",
                accessor: "owner",
                Cell: (arg: C) => teamToName(arg.cell.value),
            },
            {
                Header: "Access",
                accessor: "access",
                Cell: (arg: C) => <AccessIcon access={arg.cell.value} />,
            },
            {
                Header: "Actions",
                accessor: "id",
                Cell: arg => {
                    const shareLink = useShareLink({
                        token: arg.row.original.token || undefined,
                        tokenToLink: (id, token) => "/schema/" + id + "?token=" + token,
                        onTokenReset: id => dispatch(actions.resetToken(id)).catch(noop),
                        onTokenDrop: id => dispatch(actions.dropToken(id)).catch(noop),
                    })
                    const changeAccess = useChangeAccess({
                        onAccessUpdate: (id, owner, access) =>
                            dispatch(actions.updateAccess(id, owner, access)).catch(noop),
                    })
                    const del = useDelete({
                        onDelete: id => dispatch(actions.deleteSchema(id)).catch(noop),
                    })
                    return (
                        <ActionMenu
                            id={arg.cell.value}
                            owner={arg.row.original.owner}
                            access={arg.row.original.access as Access}
                            description={"schema " + arg.row.original.name + " (" + arg.row.original.uri + ")"}
                            items={[shareLink, changeAccess, del]}
                        />
                    )
                },
            },
        ],
        [dispatch]
    )
    const [reloadCounter, setReloadCounter] = useState(0)
    const teams = useSelector(teamsSelector)
    useEffect(() => {
        dispatch(actions.all()).catch(noop)
    }, [dispatch, teams, reloadCounter])
    const isTester = useTester()
    return (
        <PageSection>
            <Card>
                {isTester && (
                    <CardHeader>
                        <ButtonLink to="/schema/_new">New Schema</ButtonLink>
                        <SchemaImportButton
                            schemas={schemas?.schemas || []}
                            onImported={() => setReloadCounter(reloadCounter + 1)}
                        />
                    </CardHeader>
                )}
                <CardBody style={{ overflowX: "auto" }}>
                    <Table columns={columns}
                    data={schemas?.schemas || []}
                    sortBy={[{ id: "name", desc: false }]}
                    isLoading={loading}
                    />
                </CardBody>
                <CardFooter style={{ textAlign: "right" }}>
                    <Pagination
                        itemCount={schemas?.count || 0}
                        perPage={perPage}
                        page={page}
                        onSetPage={(e, p) => setPage(p)}
                        onPerPageSelect={(e, pp) => setPerPage(pp)}
                    />
                </CardFooter>
            </Card>
        </PageSection>
    )
}
