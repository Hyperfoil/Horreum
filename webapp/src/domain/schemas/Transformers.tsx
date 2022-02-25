import { useEffect, useState } from "react"
import { useDispatch, useSelector } from "react-redux"

import {
    Bullseye,
    Button,
    EmptyState,
    EmptyStateBody,
    Flex,
    FlexItem,
    Form,
    FormGroup,
    FormSection,
    Popover,
    SimpleList,
    SimpleListItem,
    Spinner,
    Split,
    SplitItem,
    TextArea,
    TextInput,
    Title,
} from "@patternfly/react-core"
import { EditIcon, HelpIcon, PlusCircleIcon } from "@patternfly/react-icons"

import { Access, defaultTeamSelector, teamToName, useTester } from "../../auth"
import { dispatchError } from "../../alerts"
import { checkAccessorName, INVALID_ACCESSOR_HELPER } from "../../components/Accessors"
import AccessIcon from "../../components/AccessIcon"
import ChangeAccessModal from "../../components/ChangeAccessModal"
import ConfirmDeleteModal from "../../components/ConfirmDeleteModal"
import JsonPathDocsLink from "../../components/JsonPathDocsLink"
import SchemaSelect from "../../components/SchemaSelect"
import { TabFunctionsRef } from "../../components/SavedTabs"
import Editor from "../../components/Editor/monaco/Editor"
import { listTransformers, addOrUpdateTransformer, testJsonPath, Transformer, NamedJsonPath } from "./api"
import TryJsonPathModal from "./TryJsonPathModal"

const TARGET_SCHEMA_HELP = (
    <>
        If the result object (or an element of the result array) does not have the <code>$schema</code> property this
        URI will be used to autofill that.
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
    </>
)

type TransformersProps = {
    schemaId: number
    schemaUri: string
    funcsRef: TabFunctionsRef
}

export default function Transformers(props: TransformersProps) {
    const [loading, setLoading] = useState(false)
    const [transformers, setTransformers] = useState<Transformer[]>([])
    const [selected, setSelected] = useState<Transformer>()
    const [deleteOpen, setDeleteOpen] = useState(false)
    const [tryJsonPath, setTryJsonPath] = useState<NamedJsonPath>()
    const [changeAccessOpen, setChangeAccessOpen] = useState(false)
    const [newAccess, setNewAccess] = useState<Access>(0)
    const [newOwner, setNewOwner] = useState<string>("__invalid_owner__")
    const defaultTeam = useSelector(defaultTeamSelector)
    const isTester = useTester(selected?.owner)
    const addTransformer = () => {
        const newTransformer: Transformer = {
            id: Math.min(...transformers.map(t => t.id - 1), -1),
            schemaId: props.schemaId,
            name: "",
            description: "",
            extractors: [],
            owner: defaultTeam || "",
            access: 0,
        }
        setTransformers([...transformers, newTransformer])
        setSelected(newTransformer)
    }
    const update = (update: Partial<Transformer>) => {
        if (!selected) {
            return
        }
        const transformer: Transformer = { ...selected, ...update, modified: true }
        setSelected(transformer)
        setTransformers(transformers.map(t => (t.id == transformer.id ? transformer : t)))
    }
    const [resetCounter, setResetCounter] = useState(0)
    props.funcsRef.current = {
        save: () =>
            Promise.all(
                transformers
                    .filter(t => t.modified)
                    .map(t =>
                        addOrUpdateTransformer(t).then(_ => {
                            t.modified = false
                        })
                    )
            ).catch(error => {
                dispatchError(dispatch, error, "TRANSFORMER_UPDATE", "Failed to update one or more transformers")
                return Promise.reject(error)
            }),
        reset: () => {
            setResetCounter(resetCounter + 1)
        },
        modified: () => transformers.some(t => t.modified),
    }
    const dispatch = useDispatch()
    useEffect(() => {
        if (typeof props.schemaId !== "number") {
            return
        }
        setLoading(true)
        setTransformers([])
        listTransformers(props.schemaId)
            .then(
                ts => {
                    setTransformers(ts)
                    if (selected === undefined && ts.length > 0) {
                        setSelected(ts[0])
                    }
                },
                error =>
                    dispatchError(
                        dispatch,
                        error,
                        "LIST_TRANSFORMERS",
                        "Failed to fetch transformers for schema " + props.schemaUri
                    )
            )
            .finally(() => setLoading(false))
    }, [props.schemaId, resetCounter])
    return (
        <>
            <Split hasGutter>
                <SplitItem style={{ minWidth: "20vw", maxWidth: "20vw", overflow: "clip" }}>
                    {loading ? (
                        <Bullseye>
                            <Spinner size="lg" />
                        </Bullseye>
                    ) : (
                        <>
                            {transformers && transformers.length > 0 && (
                                <SimpleList
                                    onSelect={(_, props) => setSelected(transformers.find(v => v.id === props.itemId))}
                                    isControlled={false}
                                >
                                    {transformers.map((t, i) => (
                                        <SimpleListItem key={i} itemId={t.id} isActive={selected?.id === t.id}>
                                            {t.name || <span style={{ color: "#888" }}>(please set the name)</span>}
                                        </SimpleListItem>
                                    ))}
                                </SimpleList>
                            )}
                            {isTester && (
                                <Button variant="link" onClick={addTransformer}>
                                    <PlusCircleIcon />
                                    {"\u00A0"}Add transformer...
                                </Button>
                            )}
                        </>
                    )}
                </SplitItem>
                <SplitItem isFilled>
                    {loading ? (
                        <Spinner size="xl" />
                    ) : selected === undefined ? (
                        <Bullseye>
                            <EmptyState>
                                <Title headingLevel="h3">No transformer selected</Title>
                                <EmptyStateBody>
                                    Please select one of the transformers on the left side or create a new one.
                                </EmptyStateBody>
                            </EmptyState>
                        </Bullseye>
                    ) : (
                        <>
                            {isTester && (
                                <div style={{ textAlign: "right", marginBottom: "16px" }}>
                                    <Button variant="danger" onClick={() => setDeleteOpen(true)}>
                                        Delete
                                    </Button>
                                    <ConfirmDeleteModal
                                        isOpen={deleteOpen}
                                        onClose={() => setDeleteOpen(false)}
                                        onDelete={() => {
                                            const newTransformers = transformers.filter(t => t.id !== selected.id)
                                            setTransformers(newTransformers)
                                            setSelected(newTransformers.length > 0 ? newTransformers[0] : undefined)
                                            return Promise.resolve()
                                        }}
                                        description={`Transformer ${selected.name}`}
                                    />
                                </div>
                            )}
                            <Form isHorizontal>
                                <FormGroup
                                    label="Name"
                                    isRequired={true}
                                    fieldId="name"
                                    helperTextInvalid="Name must not be empty"
                                >
                                    <TextInput
                                        value={selected.name}
                                        isRequired
                                        type="text"
                                        id="name"
                                        aria-describedby="name-helper"
                                        name="name"
                                        isReadOnly={!isTester}
                                        validated={selected?.name.trim() ? "default" : "error"}
                                        onChange={name => update({ name })}
                                    />
                                </FormGroup>
                                <FormGroup label="Description" fieldId="description">
                                    <TextArea
                                        id="description"
                                        isDisabled={!isTester}
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
                                    {isTester ? (
                                        <SchemaSelect
                                            value={selected.targetSchemaUri}
                                            onChange={targetSchemaUri => update({ targetSchemaUri })}
                                            noSchemaOption={true}
                                            isCreatable={true}
                                        ></SchemaSelect>
                                    ) : (
                                        <TextInput
                                            id="targetSchemaUri"
                                            value={selected.targetSchemaUri}
                                            isReadOnly={true}
                                        />
                                    )}
                                </FormGroup>
                                <FormGroup label="Ownership&amp;Access" fieldId="owner">
                                    <AccessIcon access={selected.access} />
                                    {"\u00A0\u2014\u00A0"}
                                    {teamToName(selected.owner)}
                                    {"\u00A0\u00A0"}
                                    <Button
                                        variant="link"
                                        onClick={() => {
                                            setNewAccess(selected.access)
                                            setNewOwner(selected.owner)
                                            setChangeAccessOpen(true)
                                        }}
                                    >
                                        <EditIcon />
                                    </Button>
                                    <ChangeAccessModal
                                        isOpen={changeAccessOpen}
                                        onClose={() => setChangeAccessOpen(false)}
                                        owner={newOwner}
                                        access={newAccess}
                                        onOwnerChange={owner => setNewOwner(owner)}
                                        onAccessChange={access => setNewAccess(access)}
                                        onUpdate={() => {
                                            update({ access: newAccess, owner: newOwner })
                                            setChangeAccessOpen(false)
                                        }}
                                    />
                                </FormGroup>
                                <FormSection title="Extractors">
                                    {selected.extractors.map((extractor, i) => {
                                        return (
                                            <Extractor
                                                key={i}
                                                extractor={extractor}
                                                isTester={isTester}
                                                onUpdate={() => update({ extractors: [...selected.extractors] })}
                                                onDelete={() =>
                                                    update({
                                                        extractors: selected.extractors.filter(e => e !== extractor),
                                                    })
                                                }
                                                onTryJsonPath={() => setTryJsonPath(extractor)}
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
                                                            { name: "", jsonpath: "" },
                                                        ],
                                                    })
                                                }}
                                            >
                                                Add extractor
                                            </Button>
                                        </div>
                                    )}
                                </FormSection>
                                <FormGroup
                                    style={{ display: "inline", marginTop: 0 }}
                                    label="Combination function"
                                    labelIcon={
                                        <Popover
                                            minWidth="50vw"
                                            maxWidth="50vw"
                                            bodyContent={TRANSFORMER_FUNCTION_HELP}
                                        >
                                            <Button variant="plain" onClick={e => e.preventDefault()}>
                                                <HelpIcon />
                                            </Button>
                                        </Popover>
                                    }
                                    fieldId="function"
                                >
                                    <div
                                        style={{
                                            minHeight: "100px",
                                            height: "100px",
                                            resize: "vertical",
                                            overflow: "auto",
                                        }}
                                    >
                                        <Editor
                                            value={
                                                !selected.function
                                                    ? ""
                                                    : typeof selected.function === "string"
                                                    ? selected.function
                                                    : (selected.function as any).toString()
                                            }
                                            onChange={value => update({ function: value })}
                                            language="typescript"
                                            options={{
                                                wordWrap: "on",
                                                wrappingIndent: "DeepIndent",
                                                readOnly: !isTester,
                                            }}
                                        />
                                    </div>
                                </FormGroup>
                            </Form>
                        </>
                    )}
                </SplitItem>
            </Split>
            <TryJsonPathModal
                uri={props.schemaUri}
                jsonpath={tryJsonPath?.jsonpath}
                onChange={jsonpath => {
                    if (!selected || !tryJsonPath) {
                        return
                    }
                    tryJsonPath.jsonpath = jsonpath
                    update({ extractors: [...selected.extractors] })
                }}
                onClose={() => setTryJsonPath(undefined)}
            />
        </>
    )
}

type ExtractorProps = {
    extractor: NamedJsonPath
    isTester: boolean
    onUpdate(): void
    onDelete(): void
    onTryJsonPath(): void
}

function Extractor(props: ExtractorProps) {
    const extractor = props.extractor
    const nameValid = checkAccessorName(extractor.name)
    return (
        <Split hasGutter>
            <SplitItem isFilled>
                <FormGroup
                    label="Name"
                    fieldId="extractorname"
                    validated={nameValid ? "default" : "warning"}
                    helperText={
                        nameValid
                            ? "The name of the extractor will be used as a field in the object passed to the calculation function."
                            : INVALID_ACCESSOR_HELPER
                    }
                >
                    <TextInput
                        id="extractorname"
                        value={extractor.name}
                        onChange={name => {
                            extractor.name = name
                            props.onUpdate()
                        }}
                        isReadOnly={!props.isTester}
                    />
                </FormGroup>
                <FormGroup
                    label="JSONPath"
                    labelIcon={<JsonPathDocsLink />}
                    fieldId="jsonpath"
                    validated={extractor.validationResult?.valid !== false ? "default" : "error"}
                    helperTextInvalid={extractor.validationResult?.reason || ""}
                >
                    <TextInput
                        id="jsonpath"
                        value={extractor.jsonpath}
                        onChange={jsonpath => {
                            extractor.jsonpath = jsonpath
                            extractor.validationResult = undefined
                            props.onUpdate()
                            if (extractor.validationTimer) {
                                clearTimeout(extractor.validationTimer)
                            }
                            extractor.validationTimer = window.setTimeout(() => {
                                if (extractor.jsonpath) {
                                    testJsonPath(extractor.jsonpath).then(result => {
                                        extractor.validationResult = result
                                        props.onUpdate()
                                    })
                                }
                            }, 1000)
                        }}
                        isReadOnly={!props.isTester}
                    />
                </FormGroup>
            </SplitItem>
            <SplitItem>
                <Flex style={{ height: "100%" }} alignItems={{ default: "alignItemsCenter" }}>
                    <FlexItem>
                        <Button isDisabled={!extractor.jsonpath} onClick={props.onTryJsonPath}>
                            Try it!
                        </Button>
                    </FlexItem>
                    <FlexItem>
                        <Button variant="danger" onClick={props.onDelete}>
                            Delete
                        </Button>
                    </FlexItem>
                </Flex>
            </SplitItem>
        </Split>
    )
}
