import React, {useContext, useEffect, useMemo, useState} from "react"
import { NavLink } from "react-router-dom"

import { Bullseye, EmptyState, EmptyStateBody, Spinner, Tooltip, Truncate } from '@patternfly/react-core';
import {Modal} from '@patternfly/react-core/deprecated';

import {datasetApi, LabelValue} from "../../api"
import {AppContext} from "../../context/AppContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import {OuterScrollContainer, Table, Tbody, Td, Th, Thead, Tr} from "@patternfly/react-table";
import { t_global_font_family_mono } from '@patternfly/react-tokens'

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
                    lv.name,
                    <Tooltip content={<code>{lv.schema.uri}</code>}>
                        <NavLink key="schema" to={`/schema/${lv.schema.id}#labels+${encodeURIComponent(lv.name)}`}>{lv.schema.name}</NavLink>
                    </Tooltip>,
                    <Truncate content={formatValue(lv.value)} style={{ fontFamily: t_global_font_family_mono.var }}/>,
                ],
            })),
        [labelValues]
    )

    // width of each column in percentage (columns are `label`, `schema`, `value`)
    const columnWidths : Array<10 | 15 | 20 | 25 | 30 | 35 | 40 | 45 | 50 | 60 | 70 | 80 | 90> = [50, 25, 25];
    
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
                    <Table aria-label="Simple Table" borders={false} isStickyHeader variant="compact">
                        <Thead>
                            <Tr>
                                {["Label", "Schema", "Value"].map((col, index) =>
                                    <Th key={index} aria-label={"header-" + index} width={columnWidths[index]}>{col}</Th>
                                )}
                            </Tr>
                        </Thead>
                        <Tbody>
                            {rows.map((row, index) =>
                                <Tr key={index}>
                                    {row.cells.map((cell, index) =>
                                        <Td key={index} modifier="breakWord">{cell}</Td>
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
