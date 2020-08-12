import React, { useState, useEffect } from 'react'
import {  useDispatch } from 'react-redux'
import { fetchDashboard } from './api'
import { Variable } from './types'
import Changes from './Changes'
import { alertAction } from '../../alerts'
import TestSelect, { SelectedTest } from '../../components/TestSelect'

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
    Title,
} from '@patternfly/react-core';
import { NavLink, useHistory } from 'react-router-dom';

export default () => {
    const history = useHistory()
    const test = new URLSearchParams(history.location.search).get("test")
    const dispatch = useDispatch()
    const [selectedTest, setSelectedTest] = useState<SelectedTest>()
    const [dashboardUrl, setDashboardUrl] = useState("")
    const [variables, setVariables] = useState<Variable[]>([])

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
                { <div style={{ display: "flex" }}>
                <TestSelect
                    placeholderText="Choose test..."
                    initialTestName={test || undefined}
                    onSelect={selection => {
                        setVariables([])
                        setDashboardUrl("")
                        setSelectedTest(selection as SelectedTest)
                    }}
                    selection={selectedTest}
                />
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