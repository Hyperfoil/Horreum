import {useState, useEffect, useContext} from "react"
import { useSelector } from "react-redux"
import { useHistory } from "react-router-dom"

import { Button, Checkbox, Flex, FlexItem, FormGroup, FormSection, TextInput } from "@patternfly/react-core"

import { defaultTeamSelector, useTester } from "../../auth"
import { TabFunctionsRef } from "../../components/SavedTabs"
import FindUsagesModal from "./FindUsagesModal"

import HelpPopover from "../../components/HelpPopover"
import OwnerAccess from "../../components/OwnerAccess"
import FunctionFormItem from "../../components/FunctionFormItem"
import JsonExtractor from "./JsonExtractor"
import SplitForm from "../../components/SplitForm"
import TestLabelModal from "./TestLabelModal"

import { Label, schemaApi, Access } from "../../api"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";


const LABEL_FUNCTION_HELP = (
    <>
        <p>
            This function will receive single argument: an object with names of the extractor as properties. There's no
            requirement on the result object; it's up to you to make sure it can be used as a value for Change
            Detection, displayed in the UI or processed in any other way.
        </p>
        <br />
        <p>The function is executed server-side in a sandbox and should not cause any side-effects.</p>
    </>
)

type LabelEx = {
    modified?: boolean
} & Label

type LabelsProps = {
    schemaId: number
    schemaUri: string
    funcsRef: TabFunctionsRef
}

export default function Labels({ schemaId, schemaUri, funcsRef }: LabelsProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [loading, setLoading] = useState(false)
    const [labels, setLabels] = useState<LabelEx[]>([])
    const [selected, setSelected] = useState<LabelEx>()
    const [resetCounter, setResetCounter] = useState(0)
    const [deleted, setDeleted] = useState<Label[]>([])
    const [findUsagesLabel, setFindUsagesLabel] = useState<string>()
    const [testLabelModalOpen, setTestLabelModalOpen] = useState(false)
    const isTester = useTester()
    const isTesterForLabel = useTester(selected?.owner || "__no_owner__")
    const defaultTeam = useSelector(defaultTeamSelector)
    funcsRef.current = {
        save: () =>
            Promise.all([
                ...labels
                    .filter(l => l.modified)
                    .map(l =>
                        schemaApi.addOrUpdateLabel(l.schemaId, l).then(id => {
                            l.id = id
                            l.modified = false
                        })
                    ),
                ...deleted.map(l =>
                    schemaApi.deleteLabel(l.id, l.schemaId).catch(e => {
                        setLabels([...labels, l])
                        throw e
                    })
                ),
            ])
                .catch(error => {
                    alerting.dispatchError( error, "LABEL_UPDATE", "Failed to update one or more labels.")
                    return Promise.reject(error)
                })
                .finally(() => setDeleted([])),
        reset: () => {
            setResetCounter(resetCounter + 1)
            setSelected(undefined)
            setDeleted([])
        },
        modified: () => labels.some(t => t.modified) || deleted.length > 0,
    }

    function update(update: Partial<Label>) {
        if (!selected) {
            return
        }
        const label = { ...selected, ...update, modified: true }
        setSelected(label)
        setLabels(labels.map(l => (l.id == label.id ? label : l)))
    }

    const history = useHistory()
    useEffect(() => {
        setLoading(true)
        schemaApi
            .labels(schemaId)
            .then(
                labels => {
                    setLabels(labels)
                    const fragmentParts = history.location.hash.split("+")
                    if (fragmentParts.length === 2 && fragmentParts[0] === "#labels") {
                        const decoded = decodeURIComponent(fragmentParts[1])
                        const label = labels.find(l => l.name === decoded)
                        if (label) {
                            setSelected(label)
                            return
                        }
                    }
                    if (labels.length > 0) {
                        setSelected(labels[0])
                    }
                },
                error =>
                    alerting.dispatchError(
                        error,
                        "LIST_LABELS",
                        "Failed to fetch labels for schema " + schemaUri
                    )
            )
            .finally(() => setLoading(false))
    }, [schemaId, resetCounter])

    return (
        <>
            <FindUsagesModal label={findUsagesLabel} onClose={() => setFindUsagesLabel(undefined)} />
            <SplitForm
                itemType="Label"
                addItemText="Add label..."
                noItemTitle="No labels"
                noItemText="This schema does not have any labels defined."
                canAddItem={isTester}
                items={labels}
                onChange={setLabels}
                selected={selected}
                onSelected={label => setSelected(label)}
                newItem={id => ({
                    id,
                    name: "",
                    extractors: [],
                    owner: defaultTeam || "",
                    access: Access.Public,
                    schemaId: schemaId,
                    filtering: true,
                    metrics: true,
                    modified: true,
                })}
                loading={loading}
                canDelete={isTesterForLabel}
                onDelete={label => {
                    if (label.id >= 0) {
                        setDeleted([...deleted, label])
                    }
                }}
                actions={<Button onClick={() => setFindUsagesLabel(selected?.name)}>Find usages</Button>}
            >
                {selected && (
                    <>
                        <FormGroup
                            label="Name"
                            isRequired={true}
                            fieldId="name"
                            helperTextInvalid="Name must not be empty"
                        >
                            <TextInput
                                value={selected?.name}
                                isRequired
                                type="text"
                                id="name"
                                aria-describedby="name-helper"
                                name="name"
                                isReadOnly={!isTesterForLabel}
                                validated={selected?.name.trim() ? "default" : "error"}
                                onChange={name => update({ name })}
                            />
                        </FormGroup>
                        <FormGroup label="Ownership&amp;Access" fieldId="owner">
                            <OwnerAccess
                                owner={selected.owner}
                                access={selected.access as Access}
                                onUpdate={(owner, access) => update({ owner, access })}
                                readOnly={!isTesterForLabel}
                            />
                        </FormGroup>
                        <FormGroup label="Usage" fieldId="usage">
                            <Flex alignItems={{ default: "alignItemsCenter" }}>
                                <FlexItem>
                                    <HelpPopover text="This label will be suggested on places for selecting or filtering datasets, e.g. by test configuration." />
                                </FlexItem>
                                <FlexItem>
                                    <Checkbox
                                        id="filtering"
                                        label="Filtering"
                                        isChecked={selected.filtering}
                                        onChange={checked => update({ filtering: checked })}
                                    />
                                </FlexItem>
                            </Flex>
                            <Flex alignItems={{ default: "alignItemsCenter" }}>
                                <FlexItem>
                                    <HelpPopover text="This label will be suggested on places where test results are displayed." />
                                </FlexItem>
                                <FlexItem>
                                    <Checkbox
                                        id="metrics"
                                        label="Metrics"
                                        isChecked={selected.metrics}
                                        onChange={checked => update({ metrics: checked })}
                                    />
                                </FlexItem>
                            </Flex>
                        </FormGroup>
                        <FormSection title="Extractors">
                            {selected.extractors.map((extractor, i) => {
                                return (
                                    <JsonExtractor
                                        schemaUri={schemaUri}
                                        key={i}
                                        jsonpathTarget="dataset"
                                        extractor={extractor}
                                        readOnly={!isTesterForLabel}
                                        onUpdate={() => update({ extractors: [...selected.extractors] })}
                                        onDelete={() =>
                                            update({
                                                extractors: selected.extractors.filter(e => e !== extractor),
                                            })
                                        }
                                    />
                                )
                            })}
                            {isTesterForLabel && (
                                <div>
                                    <Button
                                        variant="primary"
                                        onClick={() => {
                                            update({
                                                extractors: [
                                                    ...selected.extractors,
                                                    { name: "", jsonpath: "", array: false },
                                                ],
                                            })
                                        }}
                                    >
                                        Add extractor
                                    </Button>
                                    <Button variant="secondary" onClick={() => setTestLabelModalOpen(true)}>
                                        Test label calculation
                                    </Button>
                                    <TestLabelModal
                                        uri={schemaUri}
                                        label={selected}
                                        isOpen={testLabelModalOpen}
                                        onClose={() => setTestLabelModalOpen(false)}
                                    />
                                </div>
                            )}
                        </FormSection>
                        <FunctionFormItem
                            label="Combination function"
                            helpText={LABEL_FUNCTION_HELP}
                            value={selected._function}
                            onChange={value => update({ _function: value })}
                            readOnly={!isTesterForLabel}
                        />
                    </>
                )}
            </SplitForm>
        </>
    )
}
