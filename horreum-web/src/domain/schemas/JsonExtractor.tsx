import { useMemo, useState } from "react"

import {
    Button,
    Flex,
    FlexItem,
    FormGroup,
    List,
    ListItem,
    Popover,
    Radio,
    Split,
    SplitItem,
    TextInput,
} from "@patternfly/react-core"
import { HelpIcon } from "@patternfly/react-icons"
import { v4 as uuidv4 } from "uuid"

import JsonPathDocsLink from "../../components/JsonPathDocsLink"
import {Extractor, sqlApi} from "../../api"
import TryJsonPathModal, { JsonPathTarget } from "./TryJsonPathModal"
import { JsonpathValidation } from "../../generated"

const RESERVED = [
    "abstract",
    "arguments",
    "await",
    "boolean",
    "break",
    "byte",
    "case",
    "catch",
    "char",
    "class",
    "const",
    "continue",
    "debugger",
    "default",
    "delete",
    "do",
    "double",
    "else",
    "enum",
    "eval",
    "export",
    "extends",
    "false",
    "final",
    "finally",
    "float",
    "for",
    "function",
    "goto",
    "if",
    "implements",
    "import",
    "in",
    "instanceof",
    "int",
    "interface",
    "let",
    "long",
    "native",
    "new",
    "null",
    "package",
    "private",
    "protected",
    "public",
    "return",
    "short",
    "static",
    "super",
    "switch",
    "synchronized",
    "this",
    "throw",
    "throws",
    "transient",
    "true",
    "try",
    "typeof",
    "var",
    "void",
    "volatile",
    "while",
    "with",
    "yield",
]

function checkName(name: string | undefined) {
    if (!name) {
        return true
    } else if (!name.match(/^[a-zA-Z_][a-zA-Z0-9_]*$/)) {
        return false
    } else if (RESERVED.includes(name)) {
        return false
    } else {
        return true
    }
}

const INVALID_NAME_HELPER = (
    <span style={{ color: "var(--pf-global--warning-color--200)" }}>
        Name should match <code>[a-zA-Z_][a-zA-Z_0-9]*</code>. It shouldn't be a Javascript-reserved word either.
    </span>
)
type ExtractorEx = {
    validationTimer?: any
    validationResult?: JsonpathValidation
} & Extractor

type JsonExtractorProps = {
    schemaUri: string
    jsonpathTarget: JsonPathTarget
    extractor: ExtractorEx
    readOnly: boolean
    onUpdate(): void
    onDelete(): void
}

export default function JsonExtractor(props: JsonExtractorProps) {
    const [modalOpen, setModalOpen] = useState(false)
    const extractor = props.extractor
    const nameValid = checkName(extractor.name)
    const variantName = useMemo(() => uuidv4(), [])
    return (
        <>
            <Split hasGutter>
                <SplitItem isFilled>
                    <FormGroup
                        label="Name"
                        fieldId="extractorname"
                        validated={nameValid ? "default" : "warning"}
                        helperText={
                            nameValid
                                ? "The name of the extractor will be used as a field in the object passed to the calculation function."
                                : INVALID_NAME_HELPER
                        }
                    >
                        <TextInput
                            id="extractorname"
                            value={extractor.name}
                            onChange={name => {
                                extractor.name = name
                                props.onUpdate()
                            }}
                            isReadOnly={props.readOnly}
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
                                        sqlApi.testJsonPath(extractor.jsonpath).then(result => {
                                            extractor.validationResult = result
                                            props.onUpdate()
                                        })
                                    }
                                }, 1000)
                            }}
                            isReadOnly={props.readOnly}
                        />
                    </FormGroup>
                    <FormGroup
                        label="Variant"
                        labelIcon={
                            <Popover
                                bodyContent={
                                    <List>
                                        <ListItem>
                                            First match: only the first JSON node matching to the path will be returned.
                                            When there's no match the value will be <code>undefined</code>.
                                        </ListItem>
                                        <ListItem>
                                            All matches: the property will be an array with all matching JSON nodes
                                            (potentially empty).
                                        </ListItem>
                                    </List>
                                }
                            >
                                <Button variant="plain" onClick={e => e.preventDefault()}>
                                    <HelpIcon />
                                </Button>
                            </Popover>
                        }
                        fieldId="variant"
                    >
                        <Flex style={{ paddingTop: "var(--pf-c-form--m-horizontal__group-label--md--PaddingTop)" }}>
                            <FlexItem>
                                <Radio
                                    name={variantName}
                                    id="first"
                                    label="First match"
                                    isChecked={!extractor.array}
                                    isDisabled={props.readOnly}
                                    onChange={checked => {
                                        extractor.array = !checked
                                        props.onUpdate()
                                    }}
                                />
                            </FlexItem>
                            <FlexItem>
                                <Radio
                                    name={variantName}
                                    id="all"
                                    label="All matches"
                                    isChecked={extractor.array}
                                    isDisabled={props.readOnly}
                                    onChange={checked => {
                                        extractor.array = checked
                                        props.onUpdate()
                                    }}
                                />
                            </FlexItem>
                        </Flex>
                    </FormGroup>
                </SplitItem>
                <SplitItem>
                    <Flex style={{ height: "100%" }} alignItems={{ default: "alignItemsCenter" }}>
                        <FlexItem>
                            <Button isDisabled={!extractor.jsonpath} onClick={() => setModalOpen(true)}>
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
            <TryJsonPathModal
                uri={props.schemaUri}
                target={props.jsonpathTarget}
                jsonpath={modalOpen ? extractor.jsonpath : undefined}
                array={extractor.array}
                onChange={(jsonpath, array) => {
                    extractor.jsonpath = jsonpath
                    extractor.array = array
                    props.onUpdate()
                }}
                onClose={() => setModalOpen(false)}
            />
        </>
    )
}
