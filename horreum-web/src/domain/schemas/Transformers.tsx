import {useContext, useEffect, useState} from "react"
import { useSelector } from "react-redux"
import { useHistory } from "react-router-dom"

import { Button, FormGroup, FormSection, Popover, TextArea, TextInput } from "@patternfly/react-core"
import { HelpIcon } from "@patternfly/react-icons"

import { defaultTeamSelector, useTester } from "../../auth"
import OwnerAccess from "../../components/OwnerAccess"
import FunctionFormItem from "../../components/FunctionFormItem"
import SchemaSelect from "../../components/SchemaSelect"
import { TabFunctionsRef } from "../../components/SavedTabs"
import SplitForm from "../../components/SplitForm"
import { schemaApi, Transformer, Access } from "../../api"
import JsonExtractor from "./JsonExtractor"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";


const TARGET_SCHEMA_HELP = (
    <>
        If the result object (or an element of the result array) does not have the <code>$schema</code> property this
        URI will be used to autofill that.
        <br />
        Note that if the result of transformer has no schema it cannot be used by Horreum as there won't be any Labels.
    </>
)
const TRANSFORMER_FUNCTION_HELP = (
    <>
        <p>
            This function will receive single argument: an object with names of the extractor as properties. It should
            return an object or an array of objects with <code>$schema</code> property identifying the schema for this
            object. If the <code>$schema</code> property is not present and <i>Target schema URI</i> is set this will be
            filled automatically. If the <i>Target schema URI</i> is not set and the object/array element does not have{" "}
            <code>$schema</code> property the result will be ignored. If the result is not an object or array of objects
            (e.g. if it is a number or string) it will be ignored, too.
        </p>
        <br />
        <p>
            For each object this transformer generates Horreum creates one <i>DataSet</i>. If there are multiple
            transformers applied to one <i>Run</i> the result objects are joined into the <i>DataSet</i> based on the
            index in the array returned from this function. If this transformer creates only single object (not an array
            with single object!) and other transformer returns an array (creating several DataSets) the result of this
            transformer is included in each of those DataSets.
        </p>
        <br />
        <p>
            When the function definition is left empty the input object will be returned (this will work as identity
            function), attaching <i>Target schema URI</i> as <code>$schema</code> (if present).
        </p>
    </>
)

type TransformersProps = {
    schemaId: number
    schemaUri: string
    funcsRef: TabFunctionsRef
}

type TransformerEx = {
    modified?: boolean
} & Transformer

export default function Transformers(props: TransformersProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [loading, setLoading] = useState(false)
    const [transformers, setTransformers] = useState<TransformerEx[]>([])
    const [selected, setSelected] = useState<TransformerEx>()
    const [deleted, setDeleted] = useState<Transformer[]>([])
    const defaultTeam = useSelector(defaultTeamSelector)
    const isTester = useTester()
    const isTesterForTransformer = useTester(selected?.owner)
    const update = (update: Partial<Transformer>) => {
        if (!selected) {
            return
        }
        const transformer = { ...selected, ...update, modified: true }
        setSelected(transformer)
        setTransformers(transformers.map(t => (t.id == transformer.id ? transformer : t)))
    }
    const [resetCounter, setResetCounter] = useState(0)
    props.funcsRef.current = {
        save: () =>
            Promise.all([
                ...transformers
                    .filter(t => t.modified)
                    .map(t =>
                        schemaApi.addOrUpdateTransformer(t.schemaId, t).then(id => {
                            t.id = id
                            t.modified = false
                        })
                    ),
                ...deleted.map(t =>
                    schemaApi.deleteTransformer(t.schemaId, t.id).catch(e => {
                        setTransformers([...transformers, t])
                        throw e
                    })
                ),
            ])
                .catch(error => {
                    alerting.dispatchError( error, "TRANSFORMER_UPDATE", "Failed to update one or more transformers")
                    return Promise.reject(error)
                })
                .finally(() => setDeleted([])),
        reset: () => {
            setResetCounter(resetCounter + 1)
            setSelected(undefined)
            setDeleted([])
        },
        modified: () => transformers.some(t => t.modified) || deleted.length > 0,
    }
    const history = useHistory()
    useEffect(() => {
        if (typeof props.schemaId !== "number") {
            return
        }
        setLoading(true)
        setTransformers([])
        schemaApi
            .listTransformers(props.schemaId)
            .then(
                ts => {
                    setTransformers(ts)
                    const fragmentParts = history.location.hash.split("+")
                    if (fragmentParts.length === 2 && fragmentParts[0] === "#transformers") {
                        const decoded = decodeURIComponent(fragmentParts[1])
                        const transformer = ts.find(t => t.name === decoded)
                        if (transformer) {
                            setSelected(transformer)
                            return
                        }
                    }
                    if (ts.length > 0) {
                        setSelected(ts[0])
                    }
                },
                error =>
                    alerting.dispatchError(
                        error,
                        "LIST_TRANSFORMERS",
                        "Failed to fetch transformers for schema " + props.schemaUri
                    )
            )
            .finally(() => setLoading(false))
    }, [props.schemaId, resetCounter])
    return (
        <SplitForm
            itemType="Transformer"
            addItemText="Add transformer..."
            noItemTitle="No transformers."
            noItemText="This test does not have any transformers defined. By default each Run is trivially transformed into single Dataset."
            canAddItem={isTester}
            items={transformers}
            onChange={setTransformers}
            selected={selected}
            onSelected={t => setSelected(t)}
            newItem={id => ({
                id,
                schemaId: props.schemaId,
                schemaUri: "",
                schemaName: "",
                name: "",
                description: "",
                extractors: [],
                owner: defaultTeam || "",
                access: Access.Public,
                modified: true,
            })}
            loading={loading}
            canDelete={isTesterForTransformer}
            onDelete={t => {
                if (t.id >= 0) {
                    setDeleted([...deleted, t])
                }
            }}
        >
            {selected && (
                <>
                    <FormGroup label="Name" isRequired={true} fieldId="name" helperTextInvalid="Name must not be empty">
                        <TextInput
                            value={selected.name}
                            isRequired
                            type="text"
                            id="name"
                            aria-describedby="name-helper"
                            name="name"
                            isReadOnly={!isTesterForTransformer}
                            validated={selected?.name.trim() ? "default" : "error"}
                            onChange={name => update({ name })}
                        />
                    </FormGroup>
                    <FormGroup label="Description" fieldId="description">
                        <TextArea
                            id="description"
                            isDisabled={!isTesterForTransformer}
                            onChange={description => update({ description })}
                            autoResize={true}
                            resizeOrientation="vertical"
                            value={selected.description}
                        ></TextArea>
                    </FormGroup>
                    <FormGroup
                        label={
                            <span style={{ whiteSpace: "nowrap" }}>
                                Target schema URI
                                {/* Cannot use labelIcon as it would wrap */}
                                <Popover bodyContent={TARGET_SCHEMA_HELP}>
                                    <Button variant="plain" onClick={e => e.preventDefault()}>
                                        <HelpIcon />
                                    </Button>
                                </Popover>
                            </span>
                        }
                        fieldId="targetSchemaUri"
                    >
                        {isTesterForTransformer ? (
                            <SchemaSelect
                                value={selected.targetSchemaUri}
                                onChange={targetSchemaUri => update({ targetSchemaUri })}
                                noSchemaOption={true}
                                isCreatable={true}
                            ></SchemaSelect>
                        ) : (
                            <TextInput id="targetSchemaUri" value={selected.targetSchemaUri} isReadOnly={true} />
                        )}
                    </FormGroup>
                    <FormGroup label="Ownership&amp;Access" fieldId="owner">
                        <OwnerAccess
                            owner={selected.owner}
                            access={selected.access as Access}
                            onUpdate={(owner, access) => update({ owner, access })}
                            readOnly={!isTesterForTransformer}
                        />
                    </FormGroup>
                    <FormSection title="Extractors">
                        {selected.extractors.map((extractor, i) => {
                            return (
                                <JsonExtractor
                                    schemaUri={props.schemaUri}
                                    jsonpathTarget="run"
                                    key={i}
                                    extractor={extractor}
                                    readOnly={!isTesterForTransformer}
                                    onUpdate={() => update({ extractors: [...selected.extractors] })}
                                    onDelete={() =>
                                        update({
                                            extractors: selected.extractors.filter(e => e !== extractor),
                                        })
                                    }
                                />
                            )
                        })}
                        {isTester && (
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
                            </div>
                        )}
                    </FormSection>
                    <FunctionFormItem
                        label="Combination function"
                        helpText={TRANSFORMER_FUNCTION_HELP}
                        value={selected._function}
                        onChange={value => update({ _function: value })}
                        readOnly={!isTesterForTransformer}
                    />
                </>
            )}
        </SplitForm>
    )
}
