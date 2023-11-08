import {useContext, useEffect, useState} from "react"

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

import {
    LabelLocation,
    LabelInFingerprint,
    LabelInReport,
    LabelInRule,
    LabelInVariable,
    LabelInView,
    SchemaDescriptor, schemaApi,
} from "../../api"
import ButtonLink from "../../components/ButtonLink"
import NameUri from "../../components/NameUri"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";


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
    const { alerting } = useContext(AppContext) as AppContextType;
    const label = props.label
    const [schemas, setSchemas] = useState<SchemaDescriptor[]>()
    const [usages, setUsages] = useState<LabelLocation[]>()
    useEffect(() => {
        if (label) {
            schemaApi.findUsages(label)
                .then(setUsages)
                .catch(e => {
                    alerting.dispatchError( e, "FETCH USAGES", "Cannot retrieve label usages.")
                    props.onClose()
                })
        }
    }, [label])
    useEffect(() => {
        if (!label) {
            return
        }
        schemaApi.allLabels(label).then(
            ls => {
                if (ls.length > 0 && ls[0].name === label) {
                    setSchemas(ls[0].schemas)
                }
            },
            e => {
                alerting.dispatchError( e, "FETCH LABELS", "Cannot retrieve schemas for label " + label)
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
                        {schemas.map((shema, i) => (
                            <ListItem key={i}>
                                {/* TODO: looks like this is not navigating anywhere but IDK why */}
                                <NameUri isLink={true} onNavigate={() => props.onClose()} descriptor={shema} />
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
