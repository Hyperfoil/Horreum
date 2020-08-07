import React, { useState, useEffect } from 'react'
import { useSelector, useDispatch } from 'react-redux'
import { all as allTestsSelector } from '../tests/selectors'
import { fetchSummary } from '../tests/actions'
import { registerAfterLogin } from '../../auth'
import { fetchDashboard } from './api'
import { Variable } from './types'
import Changes from './Changes'
import { alertAction } from '../../alerts'

import {
    Bullseye,
    Button,
    Card,
    CardBody,
    CardHeader,
    DataList,
    DataListItem,
    DataListItemRow,
    DataListItemCells,
    DataListCell,
    EmptyState,
    EmptyStateBody,
    Select,
    SelectVariant,
    SelectOption,
    SelectOptionObject,
    Title,
} from '@patternfly/react-core';
import { NavLink, useHistory } from 'react-router-dom';

interface SelectedTest extends SelectOptionObject {
    id: number
}

export default () => {
    const history = useHistory()
    const test = new URLSearchParams(history.location.search).get("test")
    const dispatch = useDispatch()
    const [testMenuOpen, setTestMenuOpen] = useState(false)
    const [selectedTest, setSelectedTest] = useState<SelectedTest>()
    const [dashboardUrl, setDashboardUrl] = useState("")
    const [variables, setVariables] = useState<Variable[]>([])

    const allTests = useSelector(allTestsSelector);
    useEffect(() => {
        dispatch(fetchSummary())
        dispatch(registerAfterLogin("reload_tests", () => {
          dispatch(fetchSummary())
        }))
    },[dispatch])
    useEffect(() => {
        if (test && allTests) {
            const byParam = allTests.find(t => t.name === test)
            if (byParam && test !== selectedTest?.toString()) {
                setSelectedTest({ id: byParam.id, toString: () => byParam.name })
            }
        }
    }, [test, allTests])

    useEffect(() => {
        if (!selectedTest) {
            if (test) {
                history.replace(history.location.pathname)
            }
            return;
        }
        history.replace(history.location.pathname + "?test=" + selectedTest)
        fetchDashboard(selectedTest.id).then(response => {
            console.log(response)
            setDashboardUrl(response.url)
            setVariables(response.variables)
        }, error => dispatch(alertAction("DASHBOARD_FETCH", "Failed to fetch dashboard", error)))
    }, [selectedTest])

    return (
        <Card>
            <CardHeader>
                { allTests && <div style={{ display: "flex" }}>
                { /* TODO: change location bar on select to allow direct links*/ }
                <Select
                    variant={SelectVariant.single}
                    aria-label="Select test"
                    onToggle={setTestMenuOpen}
                    isOpen={testMenuOpen}
                    onSelect={(event, selection, isPlaceholder) => {
                        setVariables([])
                        setDashboardUrl("")
                        setSelectedTest(isPlaceholder ? undefined : selection as SelectedTest)
                        setTestMenuOpen(false)
                    }}
                    selections={selectedTest}
                >
                { [
                    (<SelectOption key={-1} value="Choose test..." isPlaceholder={true} />),
                      ...(allTests.map((test, i) => {
                          const value: SelectedTest = { id: test.id, toString: () => test.name }
                          return (<SelectOption key={i} value={value}/>)
                      }))
                ] }
                </Select>
                { selectedTest && <>
                <NavLink className="pf-c-button pf-m-primary"
                         to={ "/test/" + selectedTest.id + "#vars" }
                >Variable definitions</NavLink>
                <Button variant="secondary"
                        onClick={() => window.open(dashboardUrl, "_blank") }
                >Open Grafana</Button>
                </>}
                </div>}
            </CardHeader>
            <CardBody>
                { selectedTest && variables && variables.map((v, i) =>
                    <DataList aria-label="test variables">
                        <DataListItem key={i} aria-labelledby="variable-name">
                            { dashboardUrl &&
                            <DataListItemRow>
                                <DataListItemCells dataListCells={[
                                    <DataListCell key="chart">
                                        <iframe key={ "gf" + i }
                                            src={ dashboardUrl + "?kiosk&viewPanel=" + (i + 1)}
                                            style={{ width: "100%", height: "400px" }} />
                                    </DataListCell>,
                                ]} />
                            </DataListItemRow>
                            }
                            <DataListItemRow>
                                <DataListItemCells dataListCells={[
                                    <DataListCell key="variable-name">
                                        <Changes varId={v.id}/>
                                    </DataListCell>
                                ]} />
                            </DataListItemRow>
                        </DataListItem>
                    </DataList>
                )}
                { !selectedTest &&
                    <Bullseye>
                        <EmptyState>
                            <Title headingLevel="h2">No test selected</Title>
                            <EmptyStateBody>Please select one of the tests above</EmptyStateBody>
                        </EmptyState>
                    </Bullseye>
                }
                { selectedTest && variables.length == 0 &&
                    <Bullseye>
                    <EmptyState>
                        <Title headingLevel="h2">Test { selectedTest.toString() } does not define any regression variables</Title>
                        <NavLink
                            className="pf-c-button pf-m-primary"
                            to={ "/test/" + selectedTest.id + "#vars" }
                        >Define regression variables</NavLink>
                    </EmptyState>
                </Bullseye>
                }
                { /* This is a workaround for Grafana not renewing the token */ }
                { /* dashboardUrl && <iframe src={ logoutUrl(dashboardUrl) } style={{ width: "1px", height: "1px" }} /> */ }
            </CardBody>
        </Card>
    )
}