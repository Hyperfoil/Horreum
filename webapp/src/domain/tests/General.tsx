import React, { useState, useRef, useEffect } from 'react';
import { useSelector, useDispatch } from 'react-redux'

import {
    Button,
    Form,
    FormGroup,
    TextArea,
    TextInput,
} from '@patternfly/react-core';

import { sendTest } from './actions';
import { alertAction, constraintValidationFormatter } from '../../alerts'

import AccessIcon from '../../components/AccessIcon'
import AccessChoice from '../../components/AccessChoice'
import OwnerSelect from '../../components/OwnerSelect'
import Editor, { ValueGetter } from '../../components/Editor/monaco/Editor'

import { Test, TestDispatch } from './reducers';

import {
    useTester,
    roleToName,
    Access,
    defaultRoleSelector
} from '../../auth'

import { TabFunctionsRef } from './Test'

type GeneralProps = {
    test?: Test,
    onTestIdChange(id: number): void,
    onModified(modified: boolean): void,
    funcsRef: TabFunctionsRef
 }

export default ({ test, onTestIdChange, onModified, funcsRef }: GeneralProps) => {
    const defaultRole = useSelector(defaultRoleSelector)
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [access, setAccess] = useState<Access>(0)
    const [owner, setOwner] = useState(test && defaultRole || undefined)
    const [compareUrl, setCompareUrl] = useState("")
    const compareUrlEditor = useRef<ValueGetter>()

    useEffect(() => {
        if (!test) {
            return
        }
        setName(test.name);
        setDescription(test.description);
        setOwner(test.owner)
        setAccess(test.access)
        setCompareUrl(test && test.compareUrl && test.compareUrl.toString() || "")
    }, [test])
    useEffect(() => {
        if (!owner) {
            setOwner(defaultRole)
        }
    }, [defaultRole])

    const thunkDispatch = useDispatch<TestDispatch>()
    const dispatch = useDispatch()
    funcsRef.current = {
        save: () => {
            const newTest: Test = {
                id: test?.id || 0,
                name,
                description,
                compareUrl: compareUrlEditor.current?.getValue(),
                owner: owner || "__test_created_without_a_role__",
                access: access,
                token: null,
            }
            return thunkDispatch(sendTest(newTest)).then(
                response => onTestIdChange(response.id),
                e => {
                    dispatch(alertAction("TEST_UPDATE_FAILED", "Test update failed", e, constraintValidationFormatter("the saved test")))
                    return Promise.reject()
                }
            )
        },
        reset: () => {
            setName(test ? test.name : "");
            setDescription(test ? test.description : "");
            setOwner(test ? test.owner : defaultRole)
            setAccess(test ? test.access : 0)
            setCompareUrl(test && test.compareUrl && test.compareUrl.toString() || "")
        }
    }

    const isTester = useTester(owner)

    return (<>
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
                    onChange={n => {
                        setName(n)
                        onModified(true)
                    }}
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
                    onChange={desc => {
                        setDescription(desc)
                        onModified(true)
                    }}
                />
            </FormGroup>
            <FormGroup label="Owner" fieldId="testOwner">
            { isTester ? (
                <OwnerSelect includeGeneral={false}
                            selection={roleToName(owner) || ""}
                            onSelect={selection => {
                                setOwner(selection.key)
                                onModified(true)
                            }} />
            ) : (
                <TextInput value={roleToName(owner) || ""} id="testOwner" isReadOnly />
            )}
            </FormGroup>
            <FormGroup label="Access rights" fieldId="testAccess">
            { isTester ? (
                <AccessChoice checkedValue={access} onChange={a => {
                    setAccess(a)
                    onModified(true)
                }} />
            ) : (
                <AccessIcon access={access} />
            )}
            </FormGroup>
            <FormGroup label="Compare URL function"
                    fieldId="compareUrl"
                    helperText="This function receives an array of ids as first argument and auth token as second. It should return URL to comparator service.">
                { compareUrl === "" ? (
                    <Button
                        variant="link"
                        onClick={ () => {
                            setCompareUrl("(ids, token) => 'http://example.com/compare?ids=' + ids.join(',')")
                            onModified(true)
                        }}
                    >Add compare function...</Button>
                ) : (
                    <div style={{ minHeight: "100px", height: "100px", resize: "vertical", overflow: "auto" }}>
                        { /* TODO: call onModified(true) */ }
                        <Editor value={ compareUrl }
                                setValueGetter={e => { compareUrlEditor.current = e }}
                                options={{ wordWrap: 'on', wrappingIndent: 'DeepIndent', language: 'typescript', readOnly: !isTester }} />
                    </div>)
                }
            </FormGroup>
        </Form>
    </>);
}