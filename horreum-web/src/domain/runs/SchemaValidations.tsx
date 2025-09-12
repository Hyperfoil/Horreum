import React, { ReactElement } from "react"

import { interleave } from "../../utils"

import { Button, Form, FormGroup, PageSection, Tooltip } from "@patternfly/react-core"
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
        <PageSection padding={{ default: "noPadding" }} style={{ paddingBlockEnd: "var(--pf-v6-c-page__main-section--PaddingBlockEnd)"}}>
            <Form isHorizontal>
                <FormGroup label="Schemas" fieldId="schemas">
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
                </FormGroup>
                {props.errors && props.errors.length > 0 && (
                    <FormGroup label="Validation errors" fieldId="none">
                        <ValidationErrorTable errors={props.errors} schemas={props.schemas} />
                    </FormGroup>
                )}
            </Form>
        </PageSection>
    )
}
