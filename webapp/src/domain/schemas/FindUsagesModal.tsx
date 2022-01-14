import { useEffect, useState } from "react"
import { useDispatch } from "react-redux"

import {
    Bullseye,
    DataList,
    DataListItem,
    DataListItemRow,
    DataListItemCells,
    DataListCell,
    List,
    ListItem,
    Modal,
    Spinner,
    Title,
} from "@patternfly/react-core"
import { NavLink } from "react-router-dom"

import { AccessorLocation, AccessorInVariable, AccessorInView, AccessorInReport, Extractor, findUsages } from "./api"
import { listExtractors } from "./actions"
import { SchemaDispatch } from "./reducers"
import { alertAction } from "../../alerts"
import ButtonLink from "../../components/ButtonLink"
import { noop } from "../../utils"

type FindUsagesModalProps = {
    accessor: string | undefined
    onClose(): void
}

function usageToCells(u: AccessorLocation) {
    switch (u.type) {
        case "TAGS": {
            return [
                <DataListCell key={0}>Tags in test {u.testName}</DataListCell>,
                <DataListCell key={1}>
                    <ButtonLink to={`/test/${u.testId}#general`}>Go to</ButtonLink>
                </DataListCell>,
            ]
        }
        case "VARIABLE": {
            const loc = u as AccessorInVariable
            return [
                <DataListCell key={0}>
                    Variable {loc.variableName} in test {loc.testName}
                </DataListCell>,
                <DataListCell key={1}>
                    <ButtonLink to={`/test/${loc.testId}#vars+${loc.variableId}`}>Go to</ButtonLink>
                </DataListCell>,
            ]
        }
        case "VIEW": {
            const loc = u as AccessorInView
            return [
                <DataListCell key={0}>
                    Column <b>{loc.header}</b> in {loc.viewName === "default" ? "default view" : "view " + loc.viewName}{" "}
                    for test <b>{loc.testName}</b>
                </DataListCell>,
                <DataListCell key={1}>
                    <ButtonLink to={`/test/${loc.testId}#views+${loc.viewId}+${loc.componentId}`}>Go to</ButtonLink>
                </DataListCell>,
            ]
        }
        case "REPORT": {
            const loc = u as AccessorInReport
            return [
                <DataListCell key={0}>
                    Report config <b>{loc.title}</b> in {loc.where} {loc.name ? loc.name : ""}
                </DataListCell>,
                <DataListCell key={1}>
                    <ButtonLink to={`/reports/table/config/${loc.configId}`}>Go to</ButtonLink>
                </DataListCell>,
            ]
        }
        default:
            return [<DataListCell>Unknown location? {u}</DataListCell>]
    }
}

export default function FindUsagesModal(props: FindUsagesModalProps) {
    const dispatch = useDispatch<SchemaDispatch>()
    const [wasOpen, setWasOpen] = useState(false)
    const [allExtractors, setAllExtractors] = useState<Extractor[]>()
    const [usages, setUsages] = useState<AccessorLocation[]>()
    useEffect(() => {
        if (props.accessor) {
            setWasOpen(true)
            findUsages(props.accessor)
                .then(setUsages)
                .catch(e => {
                    dispatch(alertAction("FETCH USAGES", "Cannot retrieve accessor usages.", e))
                    props.onClose()
                })
        }
    }, [props.accessor])
    useEffect(() => {
        if (!wasOpen) {
            // prevent fetching all extractors until the modal is open
            return
        }
        dispatch(listExtractors()).then(setAllExtractors).catch(noop)
    }, [wasOpen])
    return (
        <Modal
            variant="medium"
            title={`Usages of accessor ${props.accessor}`}
            isOpen={!!props.accessor}
            onClose={() => {
                setUsages(undefined)
                props.onClose()
            }}
        >
            <div style={{ minHeight: "60vh", maxHeight: "60vh", overflowY: "auto" }}>
                <Title headingLevel="h3">Accessor is defined in these schemas:</Title>
                {allExtractors ? (
                    <List isPlain isBordered>
                        {allExtractors
                            .filter(ex => ex.accessor === props.accessor)
                            .map((ex, i) => (
                                <ListItem key={i}>
                                    {ex.schemaId ? (
                                        <NavLink to={`/schema/${ex.schemaId}`}>{ex.schema}</NavLink>
                                    ) : (
                                        ex.schema
                                    )}
                                </ListItem>
                            ))}
                    </List>
                ) : (
                    <Bullseye>
                        <Spinner size="xl" />
                    </Bullseye>
                )}
                <br />
                <br />
                {usages ? (
                    usages.length > 0 ? (
                        <>
                            <Title headingLevel="h3">These are the places where {props.accessor} is used:</Title>
                            <DataList aria-label="Accessor usages" isCompact>
                                {usages.map((u, i) => (
                                    <DataListItem key={i}>
                                        <DataListItemRow>
                                            <DataListItemCells dataListCells={usageToCells(u)}></DataListItemCells>
                                        </DataListItemRow>
                                    </DataListItem>
                                ))}
                            </DataList>
                        </>
                    ) : (
                        <Title headingLevel="h3">Accessor is not used anywhere.</Title>
                    )
                ) : (
                    <Bullseye>
                        <Spinner size="xl" />
                    </Bullseye>
                )}
            </div>
        </Modal>
    )
}
