import React, { MutableRefObject } from 'react';
import { useSelector } from 'react-redux'

import {
    Form,
    FormGroup,
    TextArea,
    TextInput,
    Toolbar,
    ToolbarContent,
    ToolbarItem,
} from '@patternfly/react-core';

import AccessIcon from '../../components/AccessIcon'
import AccessChoice from '../../components/AccessChoice'
import OwnerSelect from '../../components/OwnerSelect'

import Editor, { ValueGetter } from '../../components/Editor/monaco/Editor'

import {
    isTesterSelector,
    roleToName,
    Access
} from '../../auth'

type GeneralProps = {
    name: string,
    onNameChange(newName: string): void,
    description: string,
    onDescriptionChange(newDesc: string) : void,
    access: Access,
    onAccessChange(newAccess: Access): void,
    owner: string | null,
    onOwnerChange(newOwner: string): void,
    compareUrl: string,
    compareUrlEditorRef: MutableRefObject<ValueGetter | undefined>
 }

export default ({ name, onNameChange, description, onDescriptionChange, access, onAccessChange, owner, onOwnerChange, compareUrl, compareUrlEditorRef }: GeneralProps) => {
    const isTester = useSelector(isTesterSelector)

    return (
        <Toolbar className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md" style={{ justifyContent: "space-between" }}>
          <ToolbarContent>
            <ToolbarItem aria-label="form">
                <Form isHorizontal={true} style={{ gridGap: "2px", width: "100%", paddingRight: "8px" }}>
                    <FormGroup label="Name" isRequired={true} fieldId="name" helperText="names must be unique" helperTextInvalid="Name must be unique and not empty">
                        <TextInput
                            value={name || ""}
                            isRequired
                            type="text"
                            id="name"
                            aria-describedby="name-helper"
                            name="name"
                            isReadOnly={!isTester}
                            validated={name !== null && name.trim().length > 0 ? "default" : "error"}
                            onChange={onNameChange}
                        />
                    </FormGroup>
                    <FormGroup label="Description" fieldId="description" helperText="" helperTextInvalid="">
                        <TextArea
                            value={description || ""}
                            type="text"
                            id="description"
                            aria-describedby="description-helper"
                            name="description"
                            readOnly={!isTester}
                            onChange={onDescriptionChange}
                        />
                    </FormGroup>
                    <FormGroup label="Owner" fieldId="testOwner">
                    { isTester ? (
                        <OwnerSelect includeGeneral={false}
                                    selection={roleToName(owner) || ""}
                                    onSelect={selection => onOwnerChange(selection.key)} />
                    ) : (
                        <TextInput value={roleToName(owner) || ""} id="testOwner" isReadOnly />
                    )}
                    </FormGroup>
                    <FormGroup label="Access rights" fieldId="testAccess">
                    { isTester ? (
                        <AccessChoice checkedValue={access} onChange={onAccessChange} />
                    ) : (
                        <AccessIcon access={access} />
                    )}
                    </FormGroup>
                    <FormGroup label="Compare URL function"
                            fieldId="compareUrl"
                            helperText="This function receives an array of ids as first argument and auth token as second. It should return URL to comparator service.">
                        <div style={{ minHeight: "100px", height: "100px", resize: "vertical", overflow: "auto" }}>
                        <Editor value={ compareUrl }
                                setValueGetter={e => { compareUrlEditorRef.current = e }}
                                options={{ wordWrap: 'on', wrappingIndent: 'DeepIndent', language: 'typescript', readOnly: !isTester }} />
                        </div>
                    </FormGroup>
                </Form>
            </ToolbarItem>
          </ToolbarContent>
        </Toolbar>);
}