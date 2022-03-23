import { useState } from "react"

import { Button, Flex, FlexItem, FormGroup, Split, SplitItem, TextInput } from "@patternfly/react-core"

import { checkAccessorName, INVALID_ACCESSOR_HELPER } from "../../components/Accessors"
import JsonPathDocsLink from "../../components/JsonPathDocsLink"
import { testJsonPath, NamedJsonPath } from "./api"
import TryJsonPathModal, { JsonPathTarget } from "./TryJsonPathModal"

type JsonExtractorProps = {
    schemaUri: string
    jsonpathTarget: JsonPathTarget
    extractor: NamedJsonPath
    readOnly: boolean
    onUpdate(): void
    onDelete(): void
}

export default function JsonExtractor(props: JsonExtractorProps) {
    const [modalOpen, setModalOpen] = useState(false)
    const extractor = props.extractor
    const nameValid = checkAccessorName(extractor.name)
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
                                        testJsonPath(extractor.jsonpath).then(result => {
                                            extractor.validationResult = result
                                            props.onUpdate()
                                        })
                                    }
                                }, 1000)
                            }}
                            isReadOnly={props.readOnly}
                        />
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
                onChange={jsonpath => {
                    extractor.jsonpath = jsonpath
                    props.onUpdate()
                }}
                onClose={() => setModalOpen(false)}
            />
        </>
    )
}
