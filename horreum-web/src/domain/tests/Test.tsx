import {useState, useEffect, useRef, useContext} from "react"
import { useParams } from "react-router"

import { useSelector } from "react-redux"

import {
    Breadcrumb,
    BreadcrumbItem,
    Bullseye,
    Card,
    CardBody,
    CardHeader,
    Flex,
    FlexItem,
    PageSection,
    Spinner,
} from "@patternfly/react-core"
import { Link } from "react-router-dom"

import ButtonLink from "../../components/ButtonLink"
import SavedTabs, { SavedTab, TabFunctions, saveFunc, resetFunc, modifiedFunc } from "../../components/SavedTabs"

import { useTester, teamsSelector } from "../../auth"
import General from "./General"
import Views from "./Views"
import ChangeDetectionForm from "./ChangeDetectionForm"
import Experiments from "./Experiments"
import TestExportImport from "./TestExportImport"
import ActionsUI from "./ActionsUI"
import Access from "./Access"
import Subscriptions from "./Subscriptions"
import Transformers from "./Transformers"
import MissingDataNotifications from "./MissingDataNotifications"
import {fetchTest, fetchViews, Test, View} from "../../api";
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";


type Params = {
    testId: string
}


export default function TestView() {
    const params = useParams<Params>()
    const [testId, setTestId] = useState(params.testId === "_new" ? 0 : parseInt(params.testId))
    const [test, setTest] = useState<Test | undefined>()
    const [views, setViews] = useState<View[]>( [])
    const [modified, setModified] = useState(false)
    const generalFuncsRef = useRef<TabFunctions>()
    const accessFuncsRef = useRef<TabFunctions>()
    const viewFuncsRef = useRef<TabFunctions>()
    const variablesFuncsRef = useRef<TabFunctions>()
    const missingDataFuncsRef = useRef<TabFunctions>()
    const experimentsFuncsRef = useRef<TabFunctions>()
    const actionsFuncsRef = useRef<TabFunctions>()
    const subscriptionsFuncsRef = useRef<TabFunctions>()
    const transformersFuncsRef = useRef<TabFunctions>()
    const [loaded, setLoaded] = useState(false)

    //replace redux
    const teams = useSelector(teamsSelector)

    const { alerting } = useContext(AppContext) as AppContextType;
    useEffect(() => {
        if (testId !== 0) {
            setLoaded(false)
            fetchTest(testId, alerting)
                .then(setTest)
                .then( () => fetchViews(testId, alerting).then(setViews) )
                .finally(() => setLoaded(true))
        }
    }, [testId, teams])

    useEffect(() => {
        document.title = (testId === 0 ? "New test" : test && test.name ? test.name : "Loading test...") + " | Horreum"
    }, [test, testId])

    //TODO:: replace redux
    const isTester = useTester(test ? test.owner : undefined)

    return (
        <PageSection>
            <Card>
                <CardHeader>
                    <Flex
                        style={{ marginLeft: "16px", marginRight: "16px", width: "100%" }}
                        fullWidth={{ default: "fullWidth" }}
                        justifyContent={{ default: "justifyContentSpaceBetween" }}
                    >
                        <FlexItem>
                            <Breadcrumb>
                                <BreadcrumbItem>
                                    <Link to="/test">Tests</Link>
                                </BreadcrumbItem>
                                <BreadcrumbItem isActive>{test?.name || "New test"}</BreadcrumbItem>
                            </Breadcrumb>
                        </FlexItem>
                        <FlexItem>
                            <ButtonLink to={`/run/dataset/list/${testId}`}>Dataset list</ButtonLink>
                            <ButtonLink to={`/run/list/${testId}`} variant="secondary">
                                Run list
                            </ButtonLink>
                        </FlexItem>
                    </Flex>
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
                                alerting.dispatchInfo("SAVE", "Saved!", "Test was successfully updated!", 3000)
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
                                    views={ views }
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
                                title="Experiments"
                                fragment="experiments"
                                onSave={saveFunc(experimentsFuncsRef)}
                                onReset={resetFunc(experimentsFuncsRef)}
                                isModified={() => modified}
                            >
                                <Experiments test={test} funcsRef={experimentsFuncsRef} onModified={setModified} />
                            </SavedTab>
                            <SavedTab
                                title="Actions"
                                fragment="actions"
                                isHidden={testId <= 0 || !isTester}
                                onSave={saveFunc(actionsFuncsRef)}
                                onReset={resetFunc(actionsFuncsRef)}
                                isModified={() => modified}
                            >
                                <ActionsUI
                                    testId={testId}
                                    testOwner={test ? test.owner : undefined}
                                    onModified={setModified}
                                    funcsRef={actionsFuncsRef}
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
                                    owner={test?.owner}
                                    originalTransformers={(test && test.transformers) || []}
                                    updateTransformers={ts => {
                                        if (test) test.transformers = ts
                                    }}
                                    funcsRef={transformersFuncsRef}
                                />
                            </SavedTab>
                            <SavedTab
                                title="Export"
                                fragment="export"
                                isHidden={testId <= 0 || !isTester}
                                onSave={() => Promise.resolve()}
                                isModified={() => false}
                            >
                                <TestExportImport name={test?.name || "test"} id={testId} />
                            </SavedTab>
                        </SavedTabs>
                    </CardBody>
                )}
            </Card>
        </PageSection>
    )
}
