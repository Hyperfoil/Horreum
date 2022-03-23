import { useEffect, useState } from "react"
import { useParams } from "react-router"
import { useSelector, useDispatch } from "react-redux"

import * as actions from "./actions"
import * as selectors from "./selectors"
import { RunsDispatch } from "./reducers"
import { formatDateTime, noop } from "../../utils"
import { teamsSelector } from "../../auth"

import { Bullseye, Card, CardHeader, CardBody, PageSection, Spinner } from "@patternfly/react-core"
import { TableComposable, Thead, Tr, Th, Tbody, Td } from "@patternfly/react-table"
import FragmentTabs, { FragmentTab } from "../../components/FragmentTabs"
import { NavLink } from "react-router-dom"
import { Description } from "./components"
import DatasetData from "./DatasetData"
import RunData from "./RunData"

export default function Run() {
    const { id: stringId } = useParams<any>()
    const id = parseInt(stringId)
    document.title = `Run ${id} | Horreum`

    const run = useSelector(selectors.get(id))
    const [loading, setLoading] = useState(false)

    const dispatch = useDispatch<RunsDispatch>()
    const teams = useSelector(teamsSelector)
    useEffect(() => {
        const urlParams = new URLSearchParams(window.location.search)
        const token = urlParams.get("token")
        setLoading(true)
        dispatch(actions.get(id, token || undefined))
            .catch(noop)
            .finally(() => setLoading(false))
    }, [dispatch, id, teams])

    return (
        <PageSection>
            {loading && (
                <Bullseye>
                    <Spinner />
                </Bullseye>
            )}
            {run && (
                <>
                    <Card>
                        <CardHeader>
                            <TableComposable variant="compact">
                                <Thead>
                                    <Tr>
                                        <Th>Id</Th>
                                        <Th>Test</Th>
                                        <Th>Start</Th>
                                        <Th>Stop</Th>
                                        <Th>Description</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    <Tr>
                                        <Td>{run.id}</Td>
                                        <Td>
                                            <NavLink to={`/test/${run.testid}`}>{run.testname || run.testid}</NavLink>
                                        </Td>
                                        <Td>{formatDateTime(run.start)}</Td>
                                        <Td>{formatDateTime(run.stop)}</Td>
                                        <Td>{Description(run.description)}</Td>
                                    </Tr>
                                </Tbody>
                            </TableComposable>
                        </CardHeader>
                        <CardBody>
                            <FragmentTabs>
                                {[
                                    ...(run.datasets || []).map((id, i) => (
                                        <FragmentTab title={`Dataset #${i + 1}`} key={id} fragment={`dataset${i}`}>
                                            <DatasetData runId={run.id} datasetId={id} />
                                        </FragmentTab>
                                    )),
                                    <FragmentTab title="Original run data" fragment="run" key="original">
                                        <RunData run={run} />
                                    </FragmentTab>,
                                ]}
                            </FragmentTabs>
                        </CardBody>
                    </Card>
                </>
            )}
        </PageSection>
    )
}
