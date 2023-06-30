import {useContext, useEffect, useRef, useState} from "react"
import { useParams } from "react-router-dom"

import {
    ActionGroup,
    Breadcrumb,
    BreadcrumbItem,
    Button,
    Card,
    CardBody,
    CardHeader,
    Bullseye,
    EmptyState,
    Spinner,
    PageSection,
} from "@patternfly/react-core"
import { Link } from "react-router-dom"

import { useTester } from "../../auth"

import {reportApi, TableReport} from "../../api"
import TableReportView from "./TableReportView"
import ButtonLink from "../../components/ButtonLink"
import PrintButton from "../../components/PrintButton"
import ReportLogModal from "./ReportLogModal"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import { SelectedTest } from "../../components/TestSelect"

export default function TableReportPage() {
    const { alerting } = useContext(AppContext) as AppContextType;
    const { id } = useParams<any>()
    const idVal = parseInt(id ?? "-1")
    const [report, setReport] = useState<TableReport>()
    const [loading, setLoading] = useState(false)
    const [logOpen, setLogOpen] = useState(false)
    useEffect(() => {
        if (idVal) {
            setLoading(true)
            document.title = "Loading report... | Horreum"
            reportApi.getTableReport(idVal)
                .then(
                    report => {
                        document.title = report.config.title + " | Horreum"
                        setReport(report)
                    },
                    error => {
                        document.title = "Error | Horreum"
                        alerting.dispatchError(error, "FETCH_REPORT", "Failed to fetch table report ")
                    }
                )
                .finally(() => setLoading(false))
        }
    }, [idVal])
    const componentRef = useRef<HTMLDivElement>(null)
    const isTester = useTester(report?.config?.test?.owner)
    const selectedTest = {id: report?.config?.test?.id} as SelectedTest
    if (loading) {
        return (
            <Bullseye>
                <Spinner size="xl" />
            </Bullseye>
        )
    }
    if (!report) {
        return (
            <Bullseye>
                <EmptyState>No report available</EmptyState>
            </Bullseye>
        )
    }
    return (
        <PageSection>
            <Card>
                <CardHeader>
                    <Breadcrumb style={{ flexGrow: 100 }}>
                        <BreadcrumbItem>
                            <Link to={`/test/${selectedTest.id}/#reports-tab`}>Reports</Link>
                        </BreadcrumbItem>
                        <BreadcrumbItem>{report.config.title}</BreadcrumbItem>
                        <BreadcrumbItem isActive>{report.id}</BreadcrumbItem>
                    </Breadcrumb>
                    <ActionGroup>
                        <PrintButton printRef={componentRef} />
                        {isTester && (
                            <>
                                <ButtonLink
                                    variant="secondary"
                                    to={`/test/${selectedTest.id}/reports/table/config/` + report.config.id + "?edit=" + idVal}
                                >
                                    Edit
                                </ButtonLink>
                                <Button
                                    variant="secondary"
                                    onClick={() => setLogOpen(true)}
                                    disabled={!report.logs || report.logs.length === 0}
                                >
                                    Show log
                                </Button>
                                <ReportLogModal
                                    logs={report.logs || []}
                                    isOpen={logOpen}
                                    onClose={() => setLogOpen(false)}
                                />
                            </>
                        )}
                    </ActionGroup>
                </CardHeader>
                <CardBody>
                    <div ref={componentRef}>
                        <TableReportView report={report} editable={isTester} />
                    </div>
                </CardBody>
            </Card>
        </PageSection>
    )
}
