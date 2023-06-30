import {useContext, useEffect, useState} from "react"
import { useParams } from "react-router-dom"
import { useSelector } from "react-redux"

import { formatDateTime } from "../../utils"
import { teamsSelector, useTester } from "../../auth"

import { Bullseye, Button, Card, CardHeader, CardBody, PageSection, Spinner } from "@patternfly/react-core"
import { Table /* data-codemods */, Thead, Tr, Th, Tbody, Td } from "@patternfly/react-table"
import FragmentTabs, { FragmentTab } from "../../components/FragmentTabs"
import OwnerAccess from "../../components/OwnerAccess"
import { NavLink } from "react-router-dom"
import { Description } from "./components"
import DatasetData from "./DatasetData"
import MetaData from "./MetaData"
import RunData from "./RunData"
import TransformationLogModal from "../tests/TransformationLogModal"
import {Access, fetchRunSummary, recalculateDatasets, RunExtended, updateAccess, updateRunAccess} from "../../api"
import {AppContext} from "../../context/appContext";
import { AppContextType} from "../../context/@types/appContextTypes";

export default function Run() {
    const { alerting } = useContext(AppContext) as AppContextType;
    const { id } = useParams<any>()
    const idVal = parseInt(id ?? "-1")
    document.title = `Run ${idVal} | Horreum`

    const [run, setRun] = useState<RunExtended | undefined>(undefined)
    const [loading, setLoading] = useState(false)
    const [recalculating, setRecalculating] = useState(false)
    const [transformationLogOpen, setTransformationLogOpen] = useState(false)
    const [updateCounter, setUpdateCounter] = useState(0)

    const teams = useSelector(teamsSelector)
    const isTester = useTester(run?.owner)

    const retransformClick = () => {
        if ( run !== undefined) {
            setRecalculating(true)
            recalculateDatasets(run.id, run.testid, alerting)
                .then(recalcDataSets => setRun({...run,datasets: recalcDataSets}))
                .finally(() => setRecalculating(false))
        }
    }

    const getRunSummary = () => {
        const urlParams = new URLSearchParams(window.location.search)
        const token = urlParams.get("token")
        setLoading(true)
        fetchRunSummary(idVal, token === null ? undefined : token, alerting).then(
            response =>setRun({data: "",schemas: [],metadata: response.hasMetadata ? "" : undefined,...response})
        ).finally(() => setLoading(false))
    }

    useEffect(() => {
        getRunSummary()
    }, [idVal, teams, updateCounter])

    const accessUpdate = (owner : string, access : Access) => {
        if( run !== undefined) {
            updateRunAccess(run.id, run.testid, owner, access, alerting).then(() => getRunSummary())
        }
    }

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
                            <Table variant="compact">
                                <Thead>
                                    <Tr>
                                        <Th>Id</Th>
                                        <Th>Test</Th>
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
                                                onUpdate={(owner, access) => accessUpdate(owner, access)}
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
                                                        onClick={retransformClick}
                                                    >
                                                        Re-transform datasets {recalculating && <Spinner size="md" />}
                                                    </Button>
                                                    <Button
                                                        variant="secondary"
                                                        style={{ marginRight: "16px" }}
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
                            </Table>
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
                                        <RunData run={run} updateCounter={updateCounter} onUpdate={() => setUpdateCounter(updateCounter + 1)} />
                                    </FragmentTab>,
                                    <FragmentTab
                                        title="Metadata"
                                        fragment="metadata"
                                        key="metadata"
                                        isHidden={!run.metadata}
                                    >
                                        <MetaData run={run} />
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
