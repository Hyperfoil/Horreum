import React, {useContext, useEffect, useMemo, useState} from "react"
import { NavLink } from "react-router-dom"

import { Bullseye, EmptyState, EmptyStateBody, Modal, Spinner } from "@patternfly/react-core"

import {datasetApi, LabelValue} from "../../api"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import {OuterScrollContainer, Table, Tbody, Td, Th, Thead, Tr} from "@patternfly/react-table";

type LabelValuesModalProps = {
    datasetId: number
    isOpen: boolean
    onClose(): void
}

function formatValue(value: any) {
    if (value === undefined) return "(undefined)"
    if (value === null) return "(null)"
    if (typeof value === "object") return JSON.stringify(value)
    return value
}

export default function LabelValuesModal(props: LabelValuesModalProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [labelValues, setLabelValues] = useState<LabelValue[]>([])
    const [loading, setLoading] = useState(false)
    useEffect(() => {
        if (!props.isOpen) {
            return
        }
        setLoading(true)
        setLabelValues([])
        datasetApi.getDatasetLabelValues(props.datasetId)
            .then(setLabelValues, error =>
                alerting.dispatchError( error, "FETCH_LABEL_VALUES", "Cannot retrieve effective values for labels.")
            )
            .finally(() => setLoading(false))
    }, [props.isOpen])
    const rows = useMemo(
        () =>
            labelValues.map(lv => ({
                cells: [
                    <React.Fragment key="name">{lv.name}</React.Fragment>,
                    <NavLink key="schema" to={`/schema/${lv.schema.id}#labels+${encodeURIComponent(lv.name)}`}>
                        {lv.schema.name} (<code>{lv.schema.uri}</code>)
                    </NavLink>,
                    <pre key="value">
                        <code>{formatValue(lv.value)}</code>
                    </pre>,
                ],
            })),
        [labelValues]
    )

    return (
        <Modal
            title="Effective label values"
            variant="large"
            onClose={props.onClose}
            isOpen={props.isOpen}
        >
            {loading && (
                <Bullseye>
                    <Spinner size="xl" />
                </Bullseye>
            )}
            {labelValues.length > 0 && (
                <OuterScrollContainer style={{ overflowY:"auto", height: "80vh" }}>
                    <Table aria-label="Simple Table" variant="compact">
                        <Thead>
                            <Tr>
                                {["Label", "Schema", "Value"].map((col, index) =>
                                    <Th key={index} aria-label={"header-" + index}>{col}</Th>
                                )}
                            </Tr>
                        </Thead>
                        <Tbody>
                            {rows.map((row, index) =>
                                <Tr key={index}>
                                    {row.cells.map((cell, index) =>
                                        <Td key={index}>{cell}</Td>
                                    )}
                                </Tr>
                            )}
                        </Tbody>
                    </Table>
                </OuterScrollContainer>
            )}
            {labelValues.length === 0 && !loading && (
                <Bullseye>
                    <EmptyState titleText="No labels found." headingLevel="h4">
                        <EmptyStateBody>Check label definitions in referenced schemas.</EmptyStateBody>
                    </EmptyState>
                </Bullseye>
            )}
        </Modal>
    )
}
