import { useMemo, useState } from "react"

import {
    Button,
    Flex,
    FlexItem,
    FormGroup,
    HelperText,
    HelperTextItem,
    FormHelperText,
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
    <span style={{ color: "var(--pf-t--global--text--color--status--warning--default)" }}>
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
                    >
                        <TextInput
                            id="extractorname"
                            value={extractor.name}
                            onChange={(_event, name) => {
                                extractor.name = name
                                props.onUpdate()
                            }}
                            readOnlyVariant={props.readOnly ? "default" : undefined}
                        />
                        <FormHelperText>
                            <HelperText>
                                <HelperTextItem variant={nameValid ? "default" : "warning"}>
                                {nameValid ? "The name of the extractor will be used as a field in the object passed to the calculation function." : INVALID_NAME_HELPER}
                                </HelperTextItem>
                            </HelperText>
                        </FormHelperText>
                    </FormGroup>
                    <FormGroup
                        label="JSONPath"
                        labelHelp={<JsonPathDocsLink />}
                        fieldId="jsonpath"
                    >
                        <TextInput
                            id="jsonpath"
                            value={extractor.jsonpath}
                            onChange={(_event, jsonpath) => {
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
                            readOnlyVariant={props.readOnly ? "default" : undefined}
                        />
                        <FormHelperText>
                            <HelperText>
                                <HelperTextItem variant={extractor.validationResult?.valid !== false ? "default" : "error"}>
                                {extractor.validationResult?.valid !== false ? "" : (extractor.validationResult?.reason||"")}
                                </HelperTextItem>
                            </HelperText>
                        </FormHelperText>
                    </FormGroup>
                    <FormGroup
                        label="Variant"
                        labelHelp={
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
                                <Button icon={<HelpIcon/>} variant="plain" onClick={e => e.preventDefault()}/>
                            </Popover>
                        }
                        fieldId="variant"
                    >
                        <Flex style={{ paddingTop: "var(--pf-t--global--spacer--control--vertical--default)" }}>
                            <FlexItem>
                                <Radio
                                    name={variantName}
                                    id="first"
                                    label="First match"
                                    isChecked={!extractor.isarray}
                                    isDisabled={props.readOnly}
                                    onChange={(_event, checked) => {
                                        extractor.isarray = !checked
                                        props.onUpdate()
                                    }}
                                />
                            </FlexItem>
                            <FlexItem>
                                <Radio
                                    name={variantName}
                                    id="all"
                                    label="All matches"
                                    isChecked={extractor.isarray}
                                    isDisabled={props.readOnly}
                                    onChange={(_event, checked) => {
                                        extractor.isarray = checked
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
                array={extractor.isarray}
                onChange={(jsonpath, array) => {
                    extractor.jsonpath = jsonpath
                    extractor.isarray = array
                    props.onUpdate()
                }}
                onClose={() => setModalOpen(false)}
            />
        </>
    )
}
