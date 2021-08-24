import { MutableRefObject, useState, useEffect, useRef } from 'react';
import { useParams } from "react-router"
import { useSelector } from 'react-redux'
import { useDispatch } from 'react-redux'
import {
    Bullseye,
    Card,
    CardBody,
    Spinner,
} from '@patternfly/react-core';

import * as actions from './actions';
import * as selectors from './selectors';
import { TestDispatch } from './reducers'

import SavedTabs, { SavedTab } from '../../components/SavedTabs'

import { useTester, teamsSelector } from '../../auth'
import { dispatchInfo } from '../../alerts'
import General from './General'
import Views from './Views'
import Variables from './Variables'
import Hooks from "./Hooks";
import Access from "./Access";
import Subscriptions from './Subscriptions'

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
    const test = useSelector(selectors.get(testId))
    const [modified, setModified] = useState(false)
    const generalFuncsRef = useRef<TabFunctions>()
    const accessFuncsRef = useRef<TabFunctions>()
    const viewFuncsRef = useRef<TabFunctions>()
    const variablesFuncsRef = useRef<TabFunctions>()
    const hooksFuncsRef = useRef<TabFunctions>()
    const subscriptionsFuncsRef = useRef<TabFunctions>()
    const [ loaded, setLoaded ] = useState(false)

    const dispatch = useDispatch();
    const thunkDispatch = useDispatch<TestDispatch>()

    const teams = useSelector(teamsSelector)
    useEffect(() => {
        if (testId !== 0) {
            setLoaded(false)
            thunkDispatch(actions.fetchTest(testId)).finally(() => setLoaded(true))
        }
    }, [dispatch, thunkDispatch, testId, teams])

    useEffect(() => {
        document.title = (testId === 0 ? "New test" : test && test.name ? test.name : "Loading test...") + " | Horreum"
    }, [test, testId])
    const isTester = useTester(test ? test.owner : undefined)

    // We need to return () => ref.current...() because this component is rendered before the child component
    // that modifies ref.current.save, using the last values - otherwise we would send old values to the server
    const saveFunc = (ref: MutableRefObject<TabFunctions | undefined>) => () => (ref.current ? ref.current.save() : Promise.resolve())
    const resetFunc = (ref: MutableRefObject<TabFunctions | undefined>) => () => ref.current?.reset()
    return (<>
            <Card style={{flexGrow:1}}>
                { !loaded && testId !== 0 && (<Bullseye><Spinner /></Bullseye>) }
                { ((loaded && test) || testId === 0) && (<>
                <CardBody>
                    <SavedTabs
                        afterSave={ () => {
                            setModified(false)
                            dispatchInfo(dispatch, "SAVE", "Saved!", "Test was succesfully updated!", 3000)
                        }}
                        afterReset={ () => setModified(false) }
                    >
                        <SavedTab
                            title="General"
                            fragment="general"
                            onSave={ saveFunc(generalFuncsRef) }
                            onReset={ resetFunc(generalFuncsRef) }
                            isModified={ () => modified }
                        >
                            <General
                                test={ test || undefined}
                                onTestIdChange={ setTestId }
                                onModified={ setModified }
                                funcsRef={generalFuncsRef}/>
                        </SavedTab>
                        <SavedTab
                            title="Access"
                            fragment="access"
                            onSave={ saveFunc(accessFuncsRef)}
                            onReset={ resetFunc(accessFuncsRef)}
                            isModified={ () => modified }
                        >
                            <Access
                                test={ test || undefined }
                                onModified={ setModified}
                                funcsRef={accessFuncsRef}
                                />
                        </SavedTab>
                        <SavedTab
                            title="Views"
                            fragment="views"
                            isHidden={ testId <= 0 }
                            onSave={ saveFunc(viewFuncsRef) }
                            onReset={ resetFunc(viewFuncsRef) }
                            isModified={ () => modified }
                        >
                            <Views
                                testId={testId}
                                testView={(test ? test.defaultView : undefined) || { name: "default", components: []}}
                                testOwner={test ? test.owner : undefined}
                                onModified={ setModified }
                                funcsRef={viewFuncsRef}
                            />
                        </SavedTab>
                        <SavedTab
                            title="Regression variables"
                            fragment="vars"
                            isHidden={ testId <= 0 }
                            onSave={ saveFunc(variablesFuncsRef) }
                            onReset={ resetFunc( variablesFuncsRef) }
                            isModified={ () => modified }
                        >
                            <Variables
                                testId={testId}
                                testName={ (test && test.name) || ""}
                                testOwner={ test ? test.owner : undefined }
                                onModified={ setModified }
                                funcsRef={ variablesFuncsRef }
                            />
                        </SavedTab>
                         <SavedTab
                            title="Webhooks"
                            fragment="hooks"
                            isHidden={ testId <= 0 || !isTester }
                            onSave={ saveFunc(hooksFuncsRef) }
                            onReset={ resetFunc( hooksFuncsRef) }
                            isModified={ () => modified }
                        >
                            <Hooks
                                testId={testId}
                                testOwner={test ? test.owner : undefined}
                                onModified={ setModified }
                                funcsRef={ hooksFuncsRef }
                            />
                        </SavedTab>
                        <SavedTab
                            title="Subscriptions"
                            fragment="subscriptions"
                            isHidden={ testId <= 0 || !isTester }
                            onSave={ saveFunc(subscriptionsFuncsRef) }
                            onReset={ resetFunc( subscriptionsFuncsRef) }
                            isModified={ () => modified }
                        >
                            <Subscriptions
                                testId={testId}
                                testOwner={ test ? test.owner : undefined }
                                onModified={ setModified }
                                funcsRef={ subscriptionsFuncsRef }
                            />
                        </SavedTab>
                    </SavedTabs>
                </CardBody>
                </>)}
            </Card>
        </>
    )
}