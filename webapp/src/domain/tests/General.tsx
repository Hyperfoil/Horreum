import React, {useState, useEffect} from 'react';
import {useSelector, useDispatch} from 'react-redux'

import {
    Button,
    Form,
    FormGroup, Grid, GridItem,
    Switch,
    TextArea,
    TextInput,
} from '@patternfly/react-core';

import {sendTest} from './actions';
import { durationToMillis, millisToDuration } from '../../utils'
import {alertAction, constraintValidationFormatter} from '../../alerts'

import Accessors from '../../components/Accessors'
import TagsSelect, { convertTags, SelectedTags } from '../../components/TagsSelect'
import OptionalFunction from '../../components/OptionalFunction'

import {Test, TestDispatch, StalenessSettings} from './reducers';

import {
    useTester,
    defaultTeamSelector,
} from '../../auth'

import {TabFunctionsRef} from './Test'

type GeneralProps = {
    test?: Test,
    onTestIdChange(id: number): void,
    onModified(modified: boolean): void,
    funcsRef: TabFunctionsRef
}

type StalenessSettingsDisplay = {
    maxStalenessStr: string
} & StalenessSettings

export default function General({test, onTestIdChange, onModified, funcsRef}: GeneralProps) {
    const defaultRole = useSelector(defaultTeamSelector)
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [compareUrl, setCompareUrl] = useState<string | undefined>(undefined)
    const [notificationsEnabled, setNotificationsEnabled] = useState(true)
    const [tags, setTags] = useState<string[]>([])
    const [tagsCalculation, setTagsCalculation] = useState<string>()
    const [stalenessSettings, setStalenessSettings] = useState<StalenessSettingsDisplay[]>([])
    const [newStalenessTags, setNewStalenessTags] = useState<SelectedTags>()

    const updateState = (test?: Test) => {
        setName(test ? test.name : "");
        setDescription(test ? test.description : "");
        setTags(test && test.tags ? test.tags.split(";").filter(t => t !== "") : []);
        setTagsCalculation(test && test.tagsCalculation)
        setCompareUrl(test?.compareUrl?.toString() || undefined)
        setNotificationsEnabled(!test || test.notificationsEnabled)
        setStalenessSettings(test?.stalenessSettings?.map(ss => ({ ...ss,
            maxStalenessStr: ss.maxStaleness ? millisToDuration(ss.maxStaleness) : ""
        })) || [])
    }

    useEffect(() => {
        if (!test) {
            return
        }
        updateState(test)
    }, [test])


    const thunkDispatch = useDispatch<TestDispatch>()
    const dispatch = useDispatch()
    funcsRef.current = {
        save: () => {
            if (stalenessSettings.some(ss => !ss.maxStaleness || ss.maxStaleness <= 0)) {
                dispatch(alertAction("TEST_UPDATE_FAILED", "Test update failed", "Invalid max staleness."))
                return Promise.reject()
            }
            const newTest: Test = {
                id: test?.id || 0,
                name,
                description,
                compareUrl: compareUrl || undefined, // when empty set to undefined
                notificationsEnabled,
                tags: tags.join(";"),
                tagsCalculation: tagsCalculation || undefined,
                owner: test?.owner || defaultRole || "__test_created_without_a_role__",
                access: test ? test.access : 2, // || notation does not work well with 0
                tokens: [],
                stalenessSettings,
            }
            return thunkDispatch(sendTest(newTest)).then(
                response => onTestIdChange(response.id),
                e => {
                    dispatch(alertAction("TEST_UPDATE_FAILED", "Test update failed", e, constraintValidationFormatter("the saved test")))
                    return Promise.reject()
                }
            )
        },
        reset: () => updateState(test)
    }

    const isTester = useTester(test?.owner)

    return (<>
        <Form isHorizontal={true} style={{gridGap: "2px", width: "100%", paddingRight: "8px"}}>
            <FormGroup label="Name" isRequired={true} fieldId="name" helperText="Test names must be unique"
                        helperTextInvalid="Name must be unique and not empty">
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

            <FormGroup label="Tags" fieldId="tags"
                       helperText="Accessors that split runs into different categories.">
                <Accessors
                    value={tags}
                    onChange={newTags => {
                        setTags(newTags)
                        onModified(true)
                    }}
                    isReadOnly={!isTester}
                    allowArray={false}/>
            </FormGroup>
            <FormGroup label="Tags calculation function" fieldId="tagsCalculation"
                       helperText="Customizable function to select tags">
                <OptionalFunction
                    readOnly={ !isTester }
                    func={ tagsCalculation }
                    defaultFunc="tags => tags"
                    addText="Add tags calculation function..."
                    undefinedText="Tags calculation function is not defined"
                    onChange={ value => {
                        setTagsCalculation(value)
                        onModified(true)
                    }}
                />
            </FormGroup>
            <FormGroup label="Compare URL function"
                       fieldId="compareUrl"
                       helperText="This function receives an array of ids as first argument and auth token as second. It should return URL to comparator service.">
                <OptionalFunction
                    readOnly={ !isTester }
                    func={ compareUrl }
                    defaultFunc="(ids, token) => 'http://example.com/compare?ids=' + ids.join(',')"
                    addText="Add compare function..."
                    undefinedText="Compare function is not defined"
                    onChange={ value => {
                        setCompareUrl(value)
                        onModified(true)
                    }}
                />
            </FormGroup>
            <FormGroup
                label="Notifications"
                fieldId="notifications"
            >
                <Switch
                    id="notifications-switch"
                    label="Notifications are enabled"
                    labelOff="Notifications are disabled"
                    isDisabled={!isTester}
                    isChecked={notificationsEnabled}
                    onChange={(value) => {
                        setNotificationsEnabled(value)
                        onModified(true)
                    }}
                />
            </FormGroup>
            <FormGroup
                label="Missing runs notifications"
                fieldId="missingRuns">
                { stalenessSettings.length === 0 && "No watchdogs defined." }
                <Grid hasGutter>
                    { stalenessSettings.map((settings, i) => (<React.Fragment key={i}>
                        <GridItem span={5}>
                            <FormGroup
                                label="Tags"
                                fieldId="tags">
                                <span style={{ position: 'relative', top: '12px' }}>{ convertTags(settings.tags) }</span>
                            </FormGroup>
                        </GridItem>
                        <GridItem span={6}>
                            <FormGroup
                                label="Max staleness"
                                helperText="e.g. 1d 2h 3m 4s"
                                fieldId="maxStaleness">
                                <TextInput
                                    value={ settings.maxStalenessStr }
                                    isRequired
                                    type="text"
                                    id="maxStaleness"
                                    isReadOnly={!isTester}
                                    validated={ settings.maxStaleness !== undefined ? "default" : "error"}
                                    onChange={value => {
                                        const newSettings = [ ...stalenessSettings ]
                                        newSettings[i].maxStalenessStr = value
                                        newSettings[i].maxStaleness = durationToMillis(value)
                                        setStalenessSettings(newSettings)
                                    }}
                                />
                            </FormGroup>
                        </GridItem>
                        <GridItem span={1}>
                            <Button onClick={() => {
                                stalenessSettings.splice(i, 1)
                                setStalenessSettings([ ...stalenessSettings ])
                            }}>Delete</Button>
                        </GridItem>
                    </React.Fragment>)) }
                </Grid>
                { isTester &&
                    <div style={{ display: "flex" }}>
                        <TagsSelect
                                    testId={ test?.id }
                                    tagFilter={ tags => !stalenessSettings.some(ss => convertTags(ss.tags) === convertTags(tags)) }
                                    selection={ newStalenessTags }
                                    onSelect={ setNewStalenessTags }
                                    addAllTagsOption={ true }
                                    showIfNoTags={true}
                                />
                        <Button
                            isDisabled={ newStalenessTags === undefined }
                            onClick={() => {
                                let copy = newStalenessTags === null ? null : { ...newStalenessTags }
                                if (copy !== null) {
                                    delete copy.toString
                                }
                                setStalenessSettings(stalenessSettings.concat({
                                    tags: copy,
                                    maxStaleness: 0,
                                    maxStalenessStr: ""
                                }))
                                setNewStalenessTags(undefined)
                            }}>Add missing run watchdog...</Button>
                    </div>
                }
            </FormGroup>
        </Form>

    </>);
}