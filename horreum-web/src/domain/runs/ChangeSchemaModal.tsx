import { useEffect, useState } from "react"

import {
	Button,
	Modal,
	Spinner
} from '@patternfly/react-core';
import {
	Select,
	SelectOption
} from '@patternfly/react-core/deprecated';

import SchemaSelect from "../../components/SchemaSelect"

type InitialSchema = {
    schema: string
    id: number
}

type PathSelectProps = {
    hasRoot: boolean
    paths: string[]
    value?: string
    onChange(path: string | undefined): void
}

const ROOT_PATH = "__root_path__"

function PathSelect(props: PathSelectProps) {
    const [isExpanded, setExpanded] = useState(false)
    const options = []
    if (props.hasRoot) {
        options.push(
            <SelectOption key={-1} value={ROOT_PATH}>
                Root schema
            </SelectOption>
        )
    }
    props.paths.forEach((option, index) => options.push(<SelectOption key={index} value={option} />))
    return (
        <Select
            aria-label="Select path"
            isOpen={isExpanded}
            onToggle={(_event, val) => setExpanded(val)}
            selections={props.value ? props.value : props.hasRoot ? ROOT_PATH : undefined}
            onClear={() => {
                setExpanded(false)
                props.onChange(undefined)
            }}
            onSelect={(_, newValue) => {
                setExpanded(false)
                if (newValue === ROOT_PATH) {
                    props.onChange(undefined)
                } else {
                    props.onChange(newValue as string)
                }
            }}
            placeholderText={props.hasRoot || props.paths.length > 0 ? "Select path..." : "No paths available"}
        >
            {options}
        </Select>
    )
}

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
            variant="small"
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
                <>
                    <PathSelect paths={props.paths} hasRoot={props.hasRoot} value={path} onChange={setPath} />
                    <SchemaSelect value={schema} onChange={setSchema} noSchemaOption={true} />
                </>
            )}
            {updating && <Spinner size="xl" />}
        </Modal>
    )
}
