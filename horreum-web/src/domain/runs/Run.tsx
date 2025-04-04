import {useContext, useEffect, useState} from "react"
import { Link, useParams } from "react-router-dom"
import { useSelector } from "react-redux"

import { formatDateTime } from "../../utils"
import { teamsSelector, useTester } from "../../auth"

import { Bullseye, Button, Card, CardHeader, CardBody, PageSection, Spinner, Toolbar, ToolbarContent, Breadcrumb, BreadcrumbItem } from "@patternfly/react-core"
import { Table /* data-codemods */, Thead, Tr, Th, Tbody, Td } from "@patternfly/react-table"
import { TrashIcon } from "@patternfly/react-icons"
import FragmentTabs, { FragmentTab } from "../../components/FragmentTabs"
import OwnerAccess from "../../components/OwnerAccess"
import { NavLink } from "react-router-dom"
import { Description } from "./components"
import DatasetData from "./DatasetData"
import MetaData from "./MetaData"
import RunData from "./RunData"
import TransformationLogModal from "../tests/TransformationLogModal"
import {
    Access,
    fetchRunSummary,
    recalculateDatasets,
    RunExtended,
    trash,
    updateRunAccess
} from "../../api"
import {AppContext} from "../../context/appContext";
import { AppContextType} from "../../context/@types/appContextTypes";
import ConfirmDeleteModal from "../../components/ConfirmDeleteModal";
import ConfirmRestoreModal from "../../components/ConfirmRestoreModal";

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
    const [confirmTrashRunModalOpen, setConfirmTrashRunModalOpen] = useState(false)
    const [confirmRestoreRunModalOpen, setConfirmRestoreRunModalOpen] = useState(false)
    const [isTrashed, setIsTrashed] = useState(run?.trashed || false)

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
        setLoading(true)
        fetchRunSummary(idVal, alerting).then(
            response => {
                setRun({data: "", schemas: [], metadata: response.hasMetadata ? "" : undefined, ...response})
                setIsTrashed(response.trashed)
            }
        ).finally(() => setLoading(false))
    }

    useEffect(() => {
        getRunSummary()
    }, [idVal, teams, updateCounter, isTrashed])

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
                    <Toolbar>
                        <ToolbarContent>
                            <Breadcrumb>
                                <BreadcrumbItem>
                                    <Link to="/test">Tests</Link>
                                </BreadcrumbItem>
                                <BreadcrumbItem>
                                    <Link to={`/test/${run.testid}#run`}>{run.testname}</Link>
                                </BreadcrumbItem>
                                <BreadcrumbItem>
                                    {run.id}
                                </BreadcrumbItem>
                            </Breadcrumb>
                        </ToolbarContent>
                    </Toolbar>
                    <Card>
                        <CardHeader>
                            <Table variant="compact">
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
                                                onUpdate={(owner, access) => accessUpdate(owner, access)}
                                            />
                                        </Td>
                                        <Td>{formatDateTime(run.start)}</Td>
                                        <Td>{formatDateTime(run.stop)}</Td>
                                        <Td>{Description(run.description || "")}</Td>
                                        <Td>
                                            {isTester && (
                                                <>
                                                    {!isTrashed && (
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
                                                            <Button
                                                                variant={"danger"}
                                                                isDisabled={isTrashed && confirmTrashRunModalOpen}
                                                                onClick={() => {
                                                                    setConfirmTrashRunModalOpen(true)
                                                                }}>
                                                                Delete
                                                            </Button>
                                                        </>
                                                    ) || (
                                                        <Button
                                                            variant={"primary"}
                                                            isDisabled={!isTrashed && confirmRestoreRunModalOpen}
                                                            onClick={() => {
                                                                setConfirmRestoreRunModalOpen(true)
                                                        }}>
                                                            Restore
                                                        </Button>
                                                    )}
                                                    <ConfirmDeleteModal
                                                        key="confirmDelete"
                                                        description={"Run " + run.id}
                                                        isOpen={confirmTrashRunModalOpen}
                                                        onClose={() => setConfirmTrashRunModalOpen(false)}
                                                        onDelete={async () => {
                                                            if (run?.id) {
                                                                await trash(alerting, run?.id)
                                                                setIsTrashed(true)
                                                            } else {
                                                                console.warn("cannot trash run as run object is null")
                                                                return Promise.resolve();
                                                            }
                                                        }}
                                                    />
                                                    <ConfirmRestoreModal
                                                        key="confirmRestore"
                                                        description={"Run " + run.id}
                                                        isOpen={confirmRestoreRunModalOpen}
                                                        onClose={() => setConfirmRestoreRunModalOpen(false)}
                                                        onRestore={async () => {
                                                            if (run?.id) {
                                                                await trash(alerting, run?.id, false)
                                                                setIsTrashed(false)
                                                            } else {
                                                                console.warn("cannot restore run as run object is null")
                                                                return Promise.resolve();
                                                            }
                                                        }}
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
