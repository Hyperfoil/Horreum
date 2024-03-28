import {useState, useEffect, useRef, useContext} from "react"
import { useParams } from "react-router-dom"

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

import SavedTabs, { SavedTab, TabFunctions, saveFunc, resetFunc, modifiedFunc } from "../../components/SavedTabs"

import { useTester, teamsSelector } from "../../auth"
import TestSettings from "./TestSettings"
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

import TestDatasets from "../runs/TestDatasets";
import Changes from "../alerting/Changes";
import Reports from "../reports/Reports";
import RunList from "../runs/RunList";

type Params = {
    testId: string
    tab: string
}


export default function TestView() {
    const {testId} = useParams<any>()
    const [testIdVal, setTestIdVal] = useState(testId === "_new" ? 0 : parseInt(testId ?? "-1"))
    const [test, setTest] = useState<Test | undefined>()
    const [views, setViews] = useState<View[]>( [])
    const [modified, setModified] = useState(false)
    const dataFuncsRef = useRef<TabFunctions>()
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
        if (testIdVal !== 0) {
            setLoaded(false)
            fetchTest(testIdVal, alerting)
                .then(setTest)
                .then( () => fetchViews(testIdVal, alerting).then(setViews) )
                .finally(() => setLoaded(true))
        }
    }, [testIdVal, teams])

    useEffect(() => {
        document.title = (testIdVal === 0 ? "New test" : test && test.name ? test.name : "Loading test...") + " | Horreum"
    }, [test, testIdVal])

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
                    </Flex>
                </CardHeader>
                {!loaded && testIdVal !== 0 && (
                    <Bullseye>
                        <Spinner />
                    </Bullseye>
                )}
                {((loaded && test) || testIdVal === 0) && (
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
                                title="Runs"
                                fragment="run"
                                isHidden={testIdVal <= 0}
                                isModified={() => modified}
                                onSave={() => Promise.resolve()}
                                onReset={() => Promise.resolve()}
                            >
                                <RunList/>
                            </SavedTab>
                            <SavedTab
                                title="Datasets"
                                fragment="data"
                                isHidden={testIdVal <= 0}
                                isModified={() => modified}
                                onSave={() => Promise.resolve()}
                                onReset={() => Promise.resolve()}
                            >
                                <TestDatasets/>
                            </SavedTab>

                            <SavedTab
                                title="Changes"
                                fragment="changes"
                                isHidden={testIdVal <= 0}
                                isModified={() => modified}
                                onSave={() => Promise.resolve()}
                                onReset={() => Promise.resolve()}
                            >

                                <Changes testID={testIdVal}/>

                            </SavedTab>

                            <SavedTab
                                title="Reports"
                                fragment="reports-tab"
                                isHidden={testIdVal <= 0}
                                isModified={() => modified}
                                onSave={() => Promise.resolve()}
                                onReset={() => Promise.resolve()}
                            >
                                <Reports testId={testIdVal} />
                            </SavedTab>

                            <SavedTab
                                title="Settings"
                                fragment="settings"
                                onSave={saveFunc(generalFuncsRef)}
                                onReset={resetFunc(generalFuncsRef)}
                                isModified={() => modified}
                            >
                                <TestSettings
                                    test={test || undefined}
                                    onTestIdChange={setTestIdVal}
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
                                isHidden={testIdVal <= 0}
                                onSave={saveFunc(viewFuncsRef)}
                                onReset={resetFunc(viewFuncsRef)}
                                isModified={() => modified}
                            >
                                <Views
                                    testId={testIdVal}
                                    views={ views }
                                    testOwner={test ? test.owner : undefined}
                                    onModified={setModified}
                                    funcsRef={viewFuncsRef}
                                />
                            </SavedTab>
                            <SavedTab
                                title="Change detection"
                                fragment="vars"
                                isHidden={testIdVal <= 0}
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
                                isHidden={testIdVal <= 0}
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
                                isHidden={testIdVal <= 0}
                                onSave={saveFunc(experimentsFuncsRef)}
                                onReset={resetFunc(experimentsFuncsRef)}
                                isModified={() => modified}
                            >
                                <Experiments test={test} funcsRef={experimentsFuncsRef} onModified={setModified} />
                            </SavedTab>
                            <SavedTab
                                title="Actions"
                                fragment="actions"
                                isHidden={testIdVal <= 0 || !isTester}
                                onSave={saveFunc(actionsFuncsRef)}
                                onReset={resetFunc(actionsFuncsRef)}
                                isModified={() => modified}
                            >
                                <ActionsUI
                                    testId={testIdVal}
                                    testOwner={test ? test.owner : undefined}
                                    onModified={setModified}
                                    funcsRef={actionsFuncsRef}
                                />
                            </SavedTab>
                            <SavedTab
                                title="Subscriptions"
                                fragment="subscriptions"
                                isHidden={testIdVal <= 0 || !isTester}
                                onSave={saveFunc(subscriptionsFuncsRef)}
                                onReset={resetFunc(subscriptionsFuncsRef)}
                                isModified={() => modified}
                            >
                                <Subscriptions
                                    testId={testIdVal}
                                    testOwner={test ? test.owner : undefined}
                                    onModified={setModified}
                                    funcsRef={subscriptionsFuncsRef}
                                />
                            </SavedTab>
                            <SavedTab
                                title="Transformers"
                                fragment="transformers"
                                isHidden={testIdVal <= 0}
                                onSave={saveFunc(transformersFuncsRef)}
                                onReset={resetFunc(transformersFuncsRef)}
                                isModified={modifiedFunc(transformersFuncsRef)}
                            >
                                <Transformers
                                    testId={testIdVal}
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
                                isHidden={testIdVal <= 0 || !isTester}
                                onSave={() => Promise.resolve()}
                                isModified={() => false}
                            >
                                <TestExportImport name={test?.name || "test"} id={testIdVal} />
                            </SavedTab>
                        </SavedTabs>
                    </CardBody>
                )}
            </Card>
        </PageSection>
    )
}
