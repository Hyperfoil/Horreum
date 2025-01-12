import React, { ReactElement } from "react"

import { interleave } from "../../utils"

import { Button, Form, FormGroup, Tooltip } from "@patternfly/react-core"
import { EditIcon, TimesIcon } from "@patternfly/react-icons"
import { NavLink } from "react-router-dom"
import { SchemaUsage, ValidationError } from "../../api"
import ValidationErrorTable from "./ValidationErrorTable"
import ErrorBadge from "../../components/ErrorBadge"

type SchemaValidationsProps = {
    schemas: SchemaUsage[]
    errors: ValidationError[]
    onEdit?(): void
    noSchema: ReactElement
}

export default function SchemaValidations(props: SchemaValidationsProps) {
    return (
        <Form isHorizontal>
            <FormGroup label="Schemas" fieldId="schemas">
                <div
                    style={{
                        paddingTop: "var(--pf-t--global--spacer--control--vertical--default)",
                    }}
                >
                    {(props.schemas &&
                        props.schemas.length > 0 &&
                        interleave(
                            props.schemas.map((schema, i) => {
                                const errors = props.errors?.filter(e => e.schemaId === schema.id) || []
                                return (
                                    <React.Fragment key={2 * i}>
                                        <Tooltip content={<code>{schema.uri} </code>}>
                                            <NavLink to={`/schema/${schema.id}`}>{schema.name}</NavLink>
                                        </Tooltip>
                                        {!schema.hasJsonSchema && (
                                            <Tooltip content="JSON schema for validation is not defined">
                                                <TimesIcon style={{ fill: "#AAA" }} />
                                            </Tooltip>
                                        )}
                                        {errors.length > 0 && <ErrorBadge>{errors.length}</ErrorBadge>}
                                        {props.onEdit && (
                                            <Button
                                                icon={<EditIcon/>}
                                                variant="link"
                                                style={{paddingTop: 0}}
                                                onClick={props.onEdit}
                                            />
                                        )}
                                    </React.Fragment>
                                )
                            }),
                            i => <br key={2 * i + 1} />
                        )) || (
                        <>
                            {props.noSchema}
                            {props.onEdit && (
                                <Button icon={<EditIcon/>} variant="link" style={{paddingTop: 0}} onClick={props.onEdit}/>
                            )}
                        </>
                    )}
                </div>
            </FormGroup>
            {props.errors && props.errors.length > 0 && (
                <FormGroup label="Validation errors" fieldId="none">
                    <ValidationErrorTable errors={props.errors} schemas={props.schemas} />
                </FormGroup>
            )}
        </Form>
    )
}
