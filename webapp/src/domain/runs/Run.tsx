import { useEffect, useState } from "react"
import { useParams } from "react-router"
import { useSelector, useDispatch } from "react-redux"

import * as actions from "./actions"
import * as selectors from "./selectors"
import { RunsDispatch } from "./reducers"
import { formatDateTime, noop } from "../../utils"
import { teamsSelector, useTester } from "../../auth"

import { Bullseye, Button, Card, CardHeader, CardBody, PageSection, Spinner } from "@patternfly/react-core"
import { TableComposable, Thead, Tr, Th, Tbody, Td } from "@patternfly/react-table"
import FragmentTabs, { FragmentTab } from "../../components/FragmentTabs"
import OwnerAccess from "../../components/OwnerAccess"
import { NavLink } from "react-router-dom"
import { Description } from "./components"
import DatasetData from "./DatasetData"
import RunData from "./RunData"
import TransformationLogModal from "../tests/TransformationLogModal"
import { Access } from "../../api"

export default function Run() {
    const { id: stringId } = useParams<any>()
    const id = parseInt(stringId)
    document.title = `Run ${id} | Horreum`

    const run = useSelector(selectors.get(id))
    const [loading, setLoading] = useState(false)
    const [recalculating, setRecalculating] = useState(false)
    const [transformationLogOpen, setTransformationLogOpen] = useState(false)

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
    const isTester = useTester(run?.owner)

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
                                        <Th>Owner</Th>
                                        <Th>Start</Th>
                                        <Th>Stop</Th>
                                        <Th>Description</Th>
                                        <Th>Actions</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    <Tr>
                                        <Td>{run.id}</Td>
                                        <Td>
                                            <NavLink to={`/test/${run.testid}`}>{run.testname || run.testid}</NavLink>
                                        </Td>
                                        <Td>
                                            <OwnerAccess
                                                owner={run.owner}
                                                access={run.access as Access}
                                                readOnly={!isTester}
                                                onUpdate={(owner, access) =>
                                                    dispatch(actions.updateAccess(run.id, run.testid, owner, access))
                                                }
                                            />
                                        </Td>
                                        <Td>{formatDateTime(run.start)}</Td>
                                        <Td>{formatDateTime(run.stop)}</Td>
                                        <Td>{Description(run.description || "")}</Td>
                                        <Td>
                                            {isTester && (
                                                <>
                                                    <Button
                                                        isDisabled={recalculating}
                                                        onClick={() => {
                                                            setRecalculating(true)
                                                            dispatch(actions.recalculateDatasets(run.id, run.testid))
                                                                .catch(noop)
                                                                .finally(() => setRecalculating(false))
                                                        }}
                                                    >
                                                        Recalculate datasets {recalculating && <Spinner size="md" />}
                                                    </Button>
                                                    <Button
                                                        variant="secondary"
                                                        onClick={() => setTransformationLogOpen(true)}
                                                    >
                                                        Transformation log
                                                    </Button>
                                                    <TransformationLogModal
                                                        testId={run.testid}
                                                        runId={run.id}
                                                        title="Transformation log"
                                                        emptyMessage="There are no messages"
                                                        isOpen={transformationLogOpen}
                                                        onClose={() => setTransformationLogOpen(false)}
                                                    />
                                                </>
                                            )}
                                        </Td>
                                    </Tr>
                                </Tbody>
                            </TableComposable>
                        </CardHeader>
                        <CardBody>
                            <FragmentTabs>
                                {[
                                    ...(run.datasets || []).map((id, i) => (
                                        <FragmentTab title={`Dataset #${i + 1}`} key={id} fragment={`dataset${i}`}>
                                            <DatasetData testId={run.testid} runId={run.id} datasetId={id} />
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
