import React, { MutableRefObject, useState, useEffect, useRef } from 'react';
import { useParams, useHistory } from "react-router"
import { Location } from 'history'
import { useSelector } from 'react-redux'
import { useDispatch } from 'react-redux'
import {
    ActionGroup,
    Bullseye,
    Button,
    Card,
    CardBody,
    CardFooter,
    Spinner,
    Tab,
    Tabs,
} from '@patternfly/react-core';

import * as actions from './actions';
import * as selectors from './selectors';

import SaveChangesModal from '../../components/SaveChangesModal'

import { useTester } from '../../auth'
import { infoActions } from '../../alerts'
import General from './General'
import Views from './Views'
import Variables from './Variables'

function initialActiveTab(location: Location) {
    if (location.hash.startsWith("#views")) {
        return 1;
    } else if (location.hash.startsWith("#vars")) {
        return 2;
    } else {
        return 0;
    }
}

type Params = {
    testId: string
}

export type TabFunctions = {
    save(): Promise<any>,
    reset(): void
}

export type TabFunctionsRef = MutableRefObject<TabFunctions | undefined>

export default () => {
    const params = useParams<Params>();
    const [testId, setTestId] = useState(params.testId === "_new" ? 0 : parseInt(params.testId))
    const history = useHistory()
    const test = useSelector(selectors.get(testId))
    const [modified, setModified] = useState(false)
    const [confirmOpen, setConfirmOpen] = useState(false)
    const generalFuncsRef = useRef<TabFunctions>()
    const viewFuncsRef = useRef<TabFunctions>()
    const variablesFuncsRef = useRef<TabFunctions>()
    const funcRefs = [ generalFuncsRef, viewFuncsRef, variablesFuncsRef ]
    const tabs = [ "general", "views", "vars"]
    const gotoTab = (index: number) => {
        setActiveTab(index)
        setNextTab(index)
        history.replace("/test/" + (testId === 0 ? "__new" : testId) + "#" + tabs[index] )
    }

    const dispatch = useDispatch();

    useEffect(() => {
        if (testId !== 0) {
            dispatch(actions.fetchTest(testId))
        }
    }, [dispatch, testId])

    useEffect(() => {
        document.title = (testId === 0 ? "New test" : test && test.name ? test.name : "Loading test...") + " | Horreum"
    }, [test])
    const isTester = useTester(test ? test.owner : undefined)

    const [saving, setSaving] = useState(false)
    const [activeTab, setActiveTab] = useState<number>(initialActiveTab(history.location))
    const [nextTab, setNextTab] = useState(activeTab)
    const save = () => {
        const funcs = funcRefs[activeTab].current
        if (funcs) {
            setSaving(true)
            return funcs.save().then(() => {
                setModified(false)
                gotoTab(nextTab)
                const info = infoActions("SAVE", "Saved!", "Test was succesfully updated!")
                dispatch(info.action)
                window.setTimeout(() => dispatch(info.clear), 3000)
            }).finally(() => setSaving(false))
        } else {
            return Promise.reject()
        }
    }

    return (<>
            <SaveChangesModal
                isOpen={confirmOpen}
                onClose={ () => setConfirmOpen(false) }
                onSave={ save }
                onReset={ () => {
                    const funcs = funcRefs[activeTab].current
                    if (funcs) {
                        funcs.reset()
                    }
                    gotoTab(nextTab)
                    setModified(false)
                }}
            />
            <Card style={{flexGrow:1}}>
                { !test && testId !== 0 && (<Bullseye><Spinner /></Bullseye>) }
                { (test || testId === 0) && (<>
                <CardBody>
                    <Tabs
                        activeKey={activeTab}
                        onSelect={(e, index) => {
                            setNextTab(index as number)
                            if (modified) {
                                setConfirmOpen(true)
                            } else {
                                gotoTab(index as number)
                            }
                        }}
                    >
                        <Tab key="general" eventKey={0} title="General">
                            <General
                                test={ test || undefined}
                                onTestIdChange={ setTestId }
                                onModified={ setModified }
                                funcsRef={generalFuncsRef}/>
                        </Tab>
                        <Tab key="views" eventKey={1} isHidden={ testId <= 0 } title="Views">
                            <Views
                                testId={testId}
                                testView={(test ? test.defaultView : undefined) || { name: "default", components: []}}
                                testOwner={test ? test.owner : undefined}
                                onModified={ setModified }
                                funcsRef={viewFuncsRef}
                            />
                        </Tab>
                        <Tab key="vars" eventKey={2} isHidden={ testId <= 0 } title="Regression variables">
                            <Variables
                                testId={testId}
                                testName={ test && test.name || ""}
                                testOwner={ test ? test.owner : undefined }
                                onModified={ setModified }
                                funcsRef={variablesFuncsRef}
                            />
                        </Tab>
                    </Tabs>
                </CardBody>
                </>)}
                <CardFooter>
                { isTester &&
                    <ActionGroup style={{ marginTop: 0 }}>
                        <Button
                            variant="primary"
                            isDisabled={saving}
                            onClick={save}
                        >{ saving ? "Saving..." : "Save" }</Button>
                    </ActionGroup>
                }
                </CardFooter>
            </Card>
        </>
    )
}