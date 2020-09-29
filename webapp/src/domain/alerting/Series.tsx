import React, { useState, useEffect } from 'react'
import {  useDispatch } from 'react-redux'
import { fetchDashboard } from './api'
import { Panel } from './types'
import { ChangesTabs } from './Changes'
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
    const [panels, setPanels] = useState<Panel[]>([])

    useEffect(() => {
        if (!selectedTest) {
            if (test) {
                history.replace(history.location.pathname)
            }
            return;
        }
        history.replace(history.location.pathname + "?test=" + selectedTest)
        fetchDashboard(selectedTest.id).then(response => {
            setDashboardUrl(response.url)
            setPanels(response.panels)
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
                        setPanels([])
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
                { selectedTest && panels && panels.map((p, i) =>
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
                                    <DataListCell key="changes">
                                        <ChangesTabs variables={p.variables} testOwner={selectedTest.owner}/>
                                    </DataListCell>
                                ]}/>
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
                { selectedTest && panels.length == 0 &&
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
            </CardBody>
        </Card>
    )
}