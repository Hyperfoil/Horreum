import { useEffect, useRef, useState } from "react"
import { useDispatch } from "react-redux"
import { useParams } from "react-router"

import {
    ActionGroup,
    Button,
    Card,
    CardBody,
    CardHeader,
    Bullseye,
    EmptyState,
    Spinner,
    PageSection,
} from "@patternfly/react-core"

import { useReactToPrint } from "react-to-print"

import { alertAction } from "../../alerts"
import { useTester } from "../../auth"

import { TableReport, getTableReport } from "./api"
import TableReportView from "./TableReportView"
import ButtonLink from "../../components/ButtonLink"

export default function TableReportPage() {
    const { id: stringId } = useParams<Record<string, string>>()
    const id = parseInt(stringId)
    const [report, setReport] = useState<TableReport>()
    const [loading, setLoading] = useState(false)
    const dispatch = useDispatch()
    useEffect(() => {
        if (id) {
            setLoading(true)
            document.title = "Loading report... | Horreum"
            getTableReport(id)
                .then(
                    report => {
                        document.title = report.config.title + " | Horreum"
                        setReport(report)
                    },
                    error => {
                        document.title = "Error | Horreum"
                        dispatch(alertAction("FETCH_REPORT", "Failed to fetch table report ", error))
                    }
                )
                .finally(() => setLoading(false))
        }
    }, [id, dispatch])
    const componentRef = useRef<HTMLDivElement>(null)
    const printHandle = useReactToPrint({
        content: () => componentRef.current,
        pageStyle: "@page { margin: 1cm; }",
    })
    const isTester = useTester(report?.config?.test?.owner)
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
                    <ActionGroup>
                        <Button
                            onClick={() => {
                                if (printHandle) printHandle()
                            }}
                        >
                            Export to PDF
                        </Button>
                        {isTester && (
                            <ButtonLink
                                variant="secondary"
                                to={"/reports/table/config/" + report.config.id + "?edit=" + id}
                            >
                                Edit
                            </ButtonLink>
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
