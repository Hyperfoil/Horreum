import { useEffect, useState } from "react"

import {Button, Form, FormGroup, Spinner} from '@patternfly/react-core';
import {Modal} from '@patternfly/react-core/deprecated';

import SchemaSelect from "../../components/SchemaSelect"
import { SimpleSelect } from "../../components/templates/SimpleSelect";

type InitialSchema = {
    schema: string
    id: number
}

const ROOT_PATH = "__root_path__"

type ChangeSchemaModalProps = {
    isOpen: boolean
    onClose(): void
    update(path: string | undefined, schemaUri: string, schemaId: number): Promise<any>
    initialSchema?: InitialSchema
    paths: string[]
    hasRoot: boolean
}

export default function ChangeSchemaModal(props: ChangeSchemaModalProps) {
    const [path, setPath] = useState<string>()
    const [schema, setSchema] = useState(props.initialSchema?.schema)
    const [schemaId, setSchemaId] = useState(props.initialSchema?.id)
    const [updating, setUpdating] = useState(false)
    const onClose = () => {
        setUpdating(false)
        setSchema("")
        props.onClose()
    }
    useEffect(() => {
        setSchema(props.initialSchema?.schema)
        setSchemaId(props.initialSchema?.id)
    }, [props.initialSchema])
    return (
        <Modal
            variant="medium"
            title={"Change run schema"}
            isOpen={props.isOpen}
            onClose={props.onClose}
            actions={[
                <Button
                    key={1}
                    variant="primary"
                    isDisabled={updating}
                    onClick={() => {
                        setUpdating(true)
                        props.update(path, schema || "", schemaId || 0).finally(onClose)
                    }}
                >
                    {(schema && "Update schema") || "Remove schema"}
                </Button>,
                <Button key={2} variant="secondary" onClick={props.onClose}>
                    Cancel
                </Button>,
            ]}
        >
            {!updating && (
                <Form isHorizontal id="run-schema-form">
                    <FormGroup label="Path">
                        <SimpleSelect
                            placeholder={props.hasRoot || props.paths.length > 0 ? "Select path..." : "No paths available"}
                            initialOptions={
                                (props.hasRoot ? [{value:ROOT_PATH, content: "Root schema", selected: path === undefined}] : [])
                                    .concat(props.paths.map(p => ({value: p, content: p, selected: p === path})))
                            }
                            onSelect={(_, item) => setPath(item === ROOT_PATH ? undefined : item as string)}
                            selected={path ? path : props.hasRoot ? ROOT_PATH : undefined}
                            isScrollable
                            toggleWidth="100%"
                            maxMenuHeight="45vh"
                            popperProps={{enableFlip: false, preventOverflow: true}}
                        />
                    </FormGroup>
                    <FormGroup label="Schema">
                        <SchemaSelect value={schema} onChange={setSchema} noSchemaOption />
                    </FormGroup>
                </Form>
            )}
            {updating && <Spinner size="xl" />}
        </Modal>
    )
}
