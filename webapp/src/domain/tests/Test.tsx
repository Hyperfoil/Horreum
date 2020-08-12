import React, { useState, useEffect, useRef } from 'react';
import { useParams, useLocation } from "react-router"
import { Location } from 'history'
import { useSelector } from 'react-redux'
import { useDispatch } from 'react-redux'
import {
    Bullseye,
    Button,
    Card,
    CardBody,
    CardFooter,
    ActionGroup,
    Spinner,
    Tab,
    Tabs,
} from '@patternfly/react-core';
import { useHistory } from 'react-router-dom';
import { ValueGetter } from '../../components/Editor/monaco/Editor'

import * as actions from './actions';
import * as selectors from './selectors';

import {
    defaultRoleSelector,
    isTesterSelector,
    Access
} from '../../auth'

import {
   alertAction,
   constraintValidationFormatter,
} from "../../alerts"

import { View, Test, TestDispatch } from './reducers';
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

export default () => {
    const params = useParams<Params>();
    const testId: number = params.testId === "_new" ? 0 : parseInt(params.testId)
    const location = useLocation()
    const test = useSelector(selectors.get(testId))
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const compareUrlEditor = useRef<ValueGetter>()
    const dispatch = useDispatch();
    const thunkDispatch = useDispatch<TestDispatch>()
    useEffect(() => {
        if (testId !== 0) {
            dispatch(actions.fetchTest(testId))
        }
    }, [dispatch, testId])
    useEffect(() => {
        if (!test) {
            return
        }
        setName(test.name);
        document.title = (testId === 0 ? "New test" : test && test.name ? test.name : "Loading test...") + " | Horreum"
        setDescription(test.description);
        if (test.defaultView) {
            setView(test.defaultView)
        }
    }, [test])
    const isTester = useSelector(isTesterSelector)
    const defaultRole = useSelector(defaultRoleSelector)
    useEffect(() => {
      setOwner(defaultRole)
    }, [defaultRole])
    const [access, setAccess] = useState<Access>(0)
    const [owner, setOwner] = useState(defaultRole)
    const [view, setView] = useState<View>({ name: "default", components: []})
    const updateRendersRef = useRef<() => void>()

    const history = useHistory()

    const [activeTab, setActiveTab] = useState<number | string>(initialActiveTab(location))
    const saveHookRef = useRef<() => Promise<void>>();
    return (
        // <PageSection>
        <React.Fragment>
            <Card style={{flexGrow:1}}>
                { !test && testId !== 0 && (<Bullseye><Spinner /></Bullseye>) }
                { (test || testId === 0) && (<>
                <CardBody>
                    <Tabs activeKey={activeTab} onSelect={(e, index) => setActiveTab(index)}>
                        <Tab key="general" eventKey={0} title="General">
                            <General name={name}
                                    onNameChange={setName}
                                    description={description}
                                    onDescriptionChange={setDescription}
                                    access={access}
                                    onAccessChange={setAccess}
                                    owner={owner}
                                    onOwnerChange={setOwner}
                                    compareUrl={(test && test.compareUrl && test?.compareUrl.toString()) || ""}
                                    compareUrlEditorRef={compareUrlEditor} />
                        </Tab>
                        <Tab key="views" eventKey={1} title="Views">
                            <Views view={view}
                                onViewChange={setView}
                                updateRendersRef={updateRendersRef} />
                        </Tab>
                        <Tab key="vars" eventKey={2} title="Regression variables">
                            <Variables testId={testId} saveHookRef={saveHookRef}/>
                        </Tab>
                    </Tabs>
                </CardBody>
                { isTester &&
                <CardFooter>
                   <ActionGroup style={{ marginTop: 0 }}>
                       <Button
                           variant="primary"
                           onClick={e => {
                               if (updateRendersRef.current) {
                                   updateRendersRef.current()
                               }
                               const newTest: Test = {
                                   id: testId,
                                   name,
                                   description,
                                   compareUrl: compareUrlEditor.current?.getValue(),
                                   defaultView: view,
                                   owner: owner || "__test_created_without_a_role__",
                                   access: access,
                                   token: null,
                               }
                               thunkDispatch(actions.sendTest(newTest)).then(
                                  () => {
                                     if (saveHookRef.current) {
                                         return saveHookRef.current()
                                     } else {
                                         return Promise.resolve()
                                     }
                                  },
                                  e => dispatch(alertAction("TEST_UPDATE_FAILED", "Test update failed", e, constraintValidationFormatter("the saved test")))
                               ).then(
                                   () => history.goBack()
                               )

                           }}
                       >Save</Button>
                       <Button className="pf-c-button pf-m-secondary" onClick={() => history.goBack()}>
                           Cancel
                       </Button>
                   </ActionGroup>
                </CardFooter>
                }
                </>)}
            </Card>
        </React.Fragment>
        // </PageSection>
    )
}