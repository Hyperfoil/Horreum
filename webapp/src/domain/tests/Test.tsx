import { MutableRefObject, useState, useEffect, useRef } from 'react';
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
import { TestDispatch } from './reducers'

import SaveChangesModal from '../../components/SaveChangesModal'

import { useTester, rolesSelector } from '../../auth'
import { dispatchInfo } from '../../alerts'
import General from './General'
import Views from './Views'
import Variables from './Variables'
import Hooks from "./Hooks";
import Access from "./Access";

const tabs = [ "#general", "#access", "#views", "#vars", "#hooks"];


function initialActiveTab(location: Location) {
    return Math.max(0, tabs.findIndex((fragment, index) => location.hash.startsWith(fragment)));
}

type Params = {
    testId: string
}

export type TabFunctions = {
    save(): Promise<any>,
    reset(): void
}

export type TabFunctionsRef = MutableRefObject<TabFunctions | undefined>

export default function Test() {
    const params = useParams<Params>();
    const [testId, setTestId] = useState(params.testId === "_new" ? 0 : parseInt(params.testId))
    const history = useHistory()
    const test = useSelector(selectors.get(testId))
    const [modified, setModified] = useState(false)
    const [confirmOpen, setConfirmOpen] = useState(false)
    const generalFuncsRef = useRef<TabFunctions>()
    const accessFuncsRef = useRef<TabFunctions>()
    const viewFuncsRef = useRef<TabFunctions>()
    const variablesFuncsRef = useRef<TabFunctions>()
    const hooksFuncsRef = useRef<TabFunctions>()
    const funcRefs = [ generalFuncsRef, accessFuncsRef, viewFuncsRef, variablesFuncsRef, hooksFuncsRef ]
    const [ loaded, setLoaded ] = useState(false)
    const gotoTab = (index: number) => {
        setActiveTab(index)
        setNextTab(index)
        history.replace("/test/" + (testId === 0 ? "__new" : testId) + tabs[index] )
    }

    const dispatch = useDispatch();
    const thunkDispatch = useDispatch<TestDispatch>()

    const roles = useSelector(rolesSelector)
    useEffect(() => {
        if (testId !== 0) {
            setLoaded(false)
            thunkDispatch(actions.fetchTest(testId)).finally(() => setLoaded(true))
        }
    }, [dispatch, thunkDispatch, testId, roles])

    useEffect(() => {
        document.title = (testId === 0 ? "New test" : test && test.name ? test.name : "Loading test...") + " | Horreum"
    }, [test, testId])
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
                dispatchInfo(dispatch, "SAVE", "Saved!", "Test was succesfully updated!", 3000);
            }, () => { /* ignore errors */ }).finally(() => setSaving(false))
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
                { !loaded && testId !== 0 && (<Bullseye><Spinner /></Bullseye>) }
                { ((loaded && test) || testId === 0) && (<>
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
                        <Tab key="access" eventKey={1} title="Access">
                            <Access
                                test={ test || undefined }
                                onModified={ setModified}
                                funcsRef={accessFuncsRef}
                                />
                        </Tab>
                        <Tab key="views" eventKey={2} isHidden={ testId <= 0 } title="Views">
                            <Views
                                testId={testId}
                                testView={(test ? test.defaultView : undefined) || { name: "default", components: []}}
                                testOwner={test ? test.owner : undefined}
                                onModified={ setModified }
                                funcsRef={viewFuncsRef}
                            />
                        </Tab>
                        <Tab key="vars" eventKey={3} isHidden={ testId <= 0 } title="Regression variables">
                            <Variables
                                testId={testId}
                                testName={ (test && test.name) || ""}
                                testOwner={ test ? test.owner : undefined }
                                onModified={ setModified }
                                funcsRef={ variablesFuncsRef }
                            />
                        </Tab>
                         <Tab key="hooks" eventKey={4} isHidden={ testId <= 0 || !isTester } title="Webhooks">
                            <Hooks
                                testId={testId}
                                testOwner={test ? test.owner : undefined}
                                onModified={ setModified }
                                funcsRef={ hooksFuncsRef }
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