import { useState, useEffect } from "react"
import { useDispatch, useSelector } from "react-redux"

import { Button, FormGroup, FormSection, TextInput } from "@patternfly/react-core"

import { Access, defaultTeamSelector, useTester } from "../../auth"
import { noop } from "../../utils"
import { dispatchError } from "../../alerts"
import { TabFunctionsRef } from "../../components/SavedTabs"

import OwnerAccess from "../../components/OwnerAccess"
import FunctionFormItem from "../../components/FunctionFormItem"
import JsonExtractor from "./JsonExtractor"
import SplitForm from "../../components/SplitForm"

import { Label, listLabels, addOrUpdateLabel, deleteLabel } from "./api"

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

type LabelsProps = {
    schemaId: number
    schemaUri: string
    funcsRef: TabFunctionsRef
}

export default function Labels(props: LabelsProps) {
    const [loading, setLoading] = useState(false)
    const [labels, setLabels] = useState<Label[]>([])
    const [selected, setSelected] = useState<Label>()
    const [resetCounter, setResetCounter] = useState(0)
    const [deleted, setDeleted] = useState<Label[]>([])
    const isTester = useTester()
    const isTesterForLabel = useTester(selected?.owner || "__no_owner__")
    const defaultTeam = useSelector(defaultTeamSelector)
    const dispatch = useDispatch()
    props.funcsRef.current = {
        save: () =>
            Promise.all([
                ...labels
                    .filter(l => l.modified)
                    .map(l =>
                        addOrUpdateLabel(l).then(id => {
                            l.id = id
                            l.modified = false
                        })
                    ),
                ...deleted.map(l =>
                    deleteLabel(l).catch(e => {
                        setLabels([...labels, l])
                        throw e
                    })
                ),
            ])
                .catch(error => {
                    dispatchError(dispatch, error, "LABEL_UPDATE", "Failed to update one or more labels.")
                    return Promise.reject(error)
                })
                .finally(() => setDeleted([])),
        reset: () => {
            setResetCounter(resetCounter + 1)
        },
        modified: () => labels.some(t => t.modified) || deleted.length > 0,
    }

    function update(update: Partial<Label>) {
        if (!selected) {
            return
        }
        const label: Label = { ...selected, ...update, modified: true }
        setSelected(label)
        setLabels(labels.map(l => (l.id == label.id ? label : l)))
    }

    useEffect(() => {
        setLoading(true)
        listLabels(props.schemaId)
            .then(
                labels => {
                    setLabels(labels)
                    if (selected === undefined && labels.length > 0) {
                        setSelected(labels[0])
                    }
                },
                error =>
                    dispatchError(
                        dispatch,
                        error,
                        "LIST_LABELS",
                        "Failed to fetch labels for schema " + props.schemaUri
                    ).catch(noop)
            )
            .finally(() => setLoading(false))
    }, [props.schemaId, resetCounter])
    return (
        <SplitForm
            itemType="Label"
            addItemText="Add label..."
            noItemTitle="No label selected"
            noItemText="Please select one of the labels on the left side or create a new one."
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
                access: 0 as Access,
                schemaId: props.schemaId,
            })}
            loading={loading}
            canDelete={isTesterForLabel}
            onDelete={label => {
                if (label.id >= 0) {
                    setDeleted([...deleted, label])
                }
            }}
        >
            {selected && (
                <>
                    <FormGroup label="Name" isRequired={true} fieldId="name" helperTextInvalid="Name must not be empty">
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
                            access={selected.access}
                            onUpdate={(owner, access) => update({ owner, access })}
                            readOnly={!isTesterForLabel}
                        />
                    </FormGroup>
                    <FormSection title="Extractors">
                        {selected.extractors.map((extractor, i) => {
                            return (
                                <JsonExtractor
                                    schemaUri={props.schemaUri}
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
                                            extractors: [...selected.extractors, { name: "", jsonpath: "" }],
                                        })
                                    }}
                                >
                                    Add extractor
                                </Button>
                            </div>
                        )}
                    </FormSection>
                    <FunctionFormItem
                        label="Combination function"
                        helpText={LABEL_FUNCTION_HELP}
                        value={selected.function}
                        onChange={value => update({ function: value })}
                        readOnly={!isTesterForLabel}
                    />
                </>
            )}
        </SplitForm>
    )
}
