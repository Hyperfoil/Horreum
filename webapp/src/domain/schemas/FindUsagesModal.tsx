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

import Api, {
    LabelLocation,
    LabelInFingerprint,
    LabelInReport,
    LabelInRule,
    LabelInVariable,
    LabelInView,
    SchemaDescriptor,
} from "../../api"
import { SchemaDispatch } from "./reducers"
import { dispatchError } from "../../alerts"
import ButtonLink from "../../components/ButtonLink"

function usageToCells(u: LabelLocation) {
    switch (u.type) {
        case "VARIABLE": {
            const loc = u as LabelInVariable
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
            const loc = u as LabelInView
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
            const loc = u as LabelInReport
            return [
                <DataListCell key={0}>
                    Report config <b>{loc.title}</b> in {loc.where} {loc.name ? loc.name : ""}
                </DataListCell>,
                <DataListCell key={1}>
                    <ButtonLink to={`/reports/table/config/${loc.configId}`}>Go to</ButtonLink>
                </DataListCell>,
            ]
        }
        case "FINGERPRINT": {
            const loc = u as LabelInFingerprint
            return [
                <DataListCell key={0}>Fingerprint for change detection in test {loc.testName}</DataListCell>,
                <DataListCell key={1}>
                    <ButtonLink to={`/test/${loc.testId}#vars`}>Go to</ButtonLink>
                </DataListCell>,
            ]
        }
        case "MISSINGDATA_RULE": {
            const loc = u as LabelInRule
            return [
                <DataListCell key={0}>
                    Missing data rule {loc.ruleName} in test {loc.testName}
                </DataListCell>,
                <DataListCell key={1}>
                    <ButtonLink to={`/test/${loc.testId}#missingdata+${loc.ruleId}`}>Go to</ButtonLink>
                </DataListCell>,
            ]
        }
        default:
            return [<DataListCell>Unknown location? {u}</DataListCell>]
    }
}

type FindUsagesModalProps = {
    label: string | undefined
    onClose(): void
}

export default function FindUsagesModal(props: FindUsagesModalProps) {
    const dispatch = useDispatch<SchemaDispatch>()
    const label = props.label
    const [schemas, setSchemas] = useState<SchemaDescriptor[]>()
    const [usages, setUsages] = useState<LabelLocation[]>()
    useEffect(() => {
        if (label) {
            Api.schemaServiceFindUsages(label)
                .then(setUsages)
                .catch(e => {
                    dispatchError(dispatch, e, "FETCH USAGES", "Cannot retrieve label usages.")
                    props.onClose()
                })
        }
    }, [label])
    useEffect(() => {
        if (!label) {
            return
        }
        Api.schemaServiceAllLabels(label).then(
            ls => {
                if (ls.length > 0 && ls[0].name === label) {
                    setSchemas(ls[0].schemas)
                }
            },
            e => {
                dispatchError(dispatch, e, "FETCH LABELS", "Cannot retrieve schemas for label " + label)
                props.onClose()
            }
        )
    }, [label])
    return (
        <Modal
            variant="medium"
            title={`Usages of label ${label}`}
            isOpen={!!label}
            onClose={() => {
                setUsages(undefined)
                props.onClose()
            }}
        >
            <div style={{ minHeight: "60vh", maxHeight: "60vh", overflowY: "auto" }}>
                <Title headingLevel="h3">
                    Label <code>{label}</code> is defined in these schemas:
                </Title>
                {schemas ? (
                    <List>
                        {schemas.map((s, i) => (
                            <ListItem key={i}>
                                {/* TODO: looks like this is not navigating anywhere but IDK why */}
                                {s.id ? (
                                    <NavLink to={`/schema/${s.id}#labels`} onClick={() => props.onClose()}>
                                        {s.name}
                                    </NavLink>
                                ) : (
                                    s.name
                                )}
                                {"\u00A0"}
                                <span
                                    style={{
                                        border: "1px solid #888",
                                        borderRadius: "4px",
                                        padding: "4px",
                                        backgroundColor: "#f0f0f0",
                                    }}
                                >
                                    {s.uri}
                                </span>
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
                            <Title headingLevel="h3">These are the places where {label} is used:</Title>
                            <DataList aria-label="Label usages" isCompact>
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
                        <Title headingLevel="h3">Label is not used anywhere.</Title>
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
