import { useState, useEffect, useRef } from "react"
import { useParams } from "react-router"
import { useSelector } from "react-redux"
import { useDispatch } from "react-redux"
import { Bullseye, Card, CardBody, CardHeader, PageSection, Spinner } from "@patternfly/react-core"

import * as actions from "./actions"
import * as selectors from "./selectors"
import { TestDispatch } from "./reducers"

import ButtonLink from "../../components/ButtonLink"
import SavedTabs, { SavedTab, TabFunctions, saveFunc, resetFunc, modifiedFunc } from "../../components/SavedTabs"

import { useTester, teamsSelector } from "../../auth"
import { dispatchInfo } from "../../alerts"
import { noop } from "../../utils"
import General from "./General"
import Views from "./Views"
import ChangeDetectionForm from "./ChangeDetectionForm"
import Hooks from "./Hooks"
import Access from "./Access"
import Subscriptions from "./Subscriptions"
import Transformers from "./Transformers"
import MissingDataNotifications from "./MissingDataNotifications"

type Params = {
    testId: string
}

export default function Test() {
    const params = useParams<Params>()
    const [testId, setTestId] = useState(params.testId === "_new" ? 0 : parseInt(params.testId))
    const test = useSelector(selectors.get(testId))
    const [modified, setModified] = useState(false)
    const generalFuncsRef = useRef<TabFunctions>()
    const accessFuncsRef = useRef<TabFunctions>()
    const viewFuncsRef = useRef<TabFunctions>()
    const variablesFuncsRef = useRef<TabFunctions>()
    const missingDataFuncsRef = useRef<TabFunctions>()
    const hooksFuncsRef = useRef<TabFunctions>()
    const subscriptionsFuncsRef = useRef<TabFunctions>()
    const transformersFuncsRef = useRef<TabFunctions>()
    const [loaded, setLoaded] = useState(false)

    const dispatch = useDispatch<TestDispatch>()

    const teams = useSelector(teamsSelector)
    useEffect(() => {
        if (testId !== 0) {
            setLoaded(false)
            dispatch(actions.fetchTest(testId))
                .catch(noop)
                .finally(() => setLoaded(true))
        }
    }, [dispatch, dispatch, testId, teams])

    useEffect(() => {
        document.title = (testId === 0 ? "New test" : test && test.name ? test.name : "Loading test...") + " | Horreum"
    }, [test, testId])
    const isTester = useTester(test ? test.owner : undefined)

    return (
        <PageSection>
            <Card>
                <CardHeader>
                    <ButtonLink to={`/run/dataset/list/${testId}`}>Dataset list</ButtonLink>
                    <ButtonLink to={`/run/list/${testId}`} variant="secondary">
                        Run list
                    </ButtonLink>
                </CardHeader>
                {!loaded && testId !== 0 && (
                    <Bullseye>
                        <Spinner />
                    </Bullseye>
                )}
                {((loaded && test) || testId === 0) && (
                    <CardBody>
                        <SavedTabs
                            afterSave={() => {
                                setModified(false)
                                dispatchInfo(dispatch, "SAVE", "Saved!", "Test was succesfully updated!", 3000)
                            }}
                            afterReset={() => setModified(false)}
                            canSave={isTester}
                        >
                            <SavedTab
                                title="General"
                                fragment="general"
                                onSave={saveFunc(generalFuncsRef)}
                                onReset={resetFunc(generalFuncsRef)}
                                isModified={() => modified}
                            >
                                <General
                                    test={test || undefined}
                                    onTestIdChange={setTestId}
                                    onModified={setModified}
                                    funcsRef={generalFuncsRef}
                                />
                            </SavedTab>
                            <SavedTab
                                title="Access"
                                fragment="access"
                                onSave={saveFunc(accessFuncsRef)}
                                onReset={resetFunc(accessFuncsRef)}
                                isModified={() => modified}
                            >
                                <Access test={test || undefined} onModified={setModified} funcsRef={accessFuncsRef} />
                            </SavedTab>
                            <SavedTab
                                title="Views"
                                fragment="views"
                                isHidden={testId <= 0}
                                onSave={saveFunc(viewFuncsRef)}
                                onReset={resetFunc(viewFuncsRef)}
                                isModified={() => modified}
                            >
                                <Views
                                    testId={testId}
                                    testView={
                                        (test ? test.defaultView : undefined) || { name: "default", components: [] }
                                    }
                                    testOwner={test ? test.owner : undefined}
                                    onModified={setModified}
                                    funcsRef={viewFuncsRef}
                                />
                            </SavedTab>
                            <SavedTab
                                title="Change detection"
                                fragment="vars"
                                isHidden={testId <= 0}
                                onSave={saveFunc(variablesFuncsRef)}
                                onReset={resetFunc(variablesFuncsRef)}
                                isModified={() => modified}
                            >
                                {/*
                                We have a problem with TestSelect used deeper down in this component:
                                whenever the CopyVarsModal is opened the test fetches most recent list of tests,
                                causing state change and reload here, too.
                                The solution is to mount the component only after the test is loaded and not
                                react to changes of the test object (only to the id).
                                */}
                                {test && test.id > 0 ? (
                                    <ChangeDetectionForm
                                        test={test}
                                        onModified={setModified}
                                        funcsRef={variablesFuncsRef}
                                    />
                                ) : (
                                    <Bullseye>
                                        <Spinner />
                                    </Bullseye>
                                )}
                            </SavedTab>
                            <SavedTab
                                title="Missing data notifications"
                                fragment="missingdata"
                                isHidden={testId <= 0}
                                onSave={saveFunc(missingDataFuncsRef)}
                                onReset={resetFunc(missingDataFuncsRef)}
                                isModified={() => modified}
                            >
                                <MissingDataNotifications
                                    test={test}
                                    onModified={setModified}
                                    funcsRef={missingDataFuncsRef}
                                />
                            </SavedTab>
                            <SavedTab
                                title="Webhooks"
                                fragment="hooks"
                                isHidden={testId <= 0 || !isTester}
                                onSave={saveFunc(hooksFuncsRef)}
                                onReset={resetFunc(hooksFuncsRef)}
                                isModified={() => modified}
                            >
                                <Hooks
                                    testId={testId}
                                    testOwner={test ? test.owner : undefined}
                                    onModified={setModified}
                                    funcsRef={hooksFuncsRef}
                                />
                            </SavedTab>
                            <SavedTab
                                title="Subscriptions"
                                fragment="subscriptions"
                                isHidden={testId <= 0 || !isTester}
                                onSave={saveFunc(subscriptionsFuncsRef)}
                                onReset={resetFunc(subscriptionsFuncsRef)}
                                isModified={() => modified}
                            >
                                <Subscriptions
                                    testId={testId}
                                    testOwner={test ? test.owner : undefined}
                                    onModified={setModified}
                                    funcsRef={subscriptionsFuncsRef}
                                />
                            </SavedTab>
                            <SavedTab
                                title="Transformers"
                                fragment="transformers"
                                isHidden={testId <= 0}
                                onSave={saveFunc(transformersFuncsRef)}
                                onReset={resetFunc(transformersFuncsRef)}
                                isModified={modifiedFunc(transformersFuncsRef)}
                            >
                                <Transformers
                                    testId={testId}
                                    originalTransformers={(test && test.transformers) || []}
                                    updateTransformers={ts => {
                                        if (test) test.transformers = ts
                                    }}
                                    funcsRef={transformersFuncsRef}
                                />
                            </SavedTab>
                        </SavedTabs>
                    </CardBody>
                )}
            </Card>
        </PageSection>
    )
}
