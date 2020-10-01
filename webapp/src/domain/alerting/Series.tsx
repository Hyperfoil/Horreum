import React, { useState, useEffect } from 'react'
import {  useDispatch } from 'react-redux'
import { fetchDashboard, fetchTags } from './api'
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
    Select,
    SelectOption,
    SelectOptionObject,
    Title,
} from '@patternfly/react-core';
import { NavLink, useHistory } from 'react-router-dom';

function convertTags(tags: any): string {
    let str = ""
    for (const [key, value] of Object.entries(tags)) {
        if (str !== "") {
            str = str + ";"
        }
        str = str + key + ":" + value
    }
    return str
}

export default () => {
    const history = useHistory()
    const params = new URLSearchParams(history.location.search)
    const test = params.get("test")
    const tags = params.get("tags")
    const dispatch = useDispatch()
    const [selectedTest, setSelectedTest] = useState<SelectedTest>()
    const [dashboardUrl, setDashboardUrl] = useState("")
    const [panels, setPanels] = useState<Panel[]>([])

    const [availableTags, setAvailableTags] = useState<any[]>()
    const [currentTags, setCurrentTags] = useState<SelectOptionObject>()
    const [tagMenuOpen, setTagMenuOpen] = useState(false)

    const doFetchDashboard = (testId: number, tags?: string) => fetchDashboard(testId, tags).then(response => {
        setDashboardUrl(response.url)
        setPanels(response.panels)
    }, error => dispatch(alertAction("DASHBOARD_FETCH", "Failed to fetch dashboard", error)))

    useEffect(() => {
        if (!selectedTest) {
            return;
        }
        if (!tags) {
            history.replace(history.location.pathname + "?test=" + selectedTest)
        }
        fetchTags(selectedTest.id).then((response: any[]) => {
            setAvailableTags(response)
            const paramTags = tags && response.find(t => convertTags(t) === tags)
            setCurrentTags(paramTags && { ...paramTags, toString: () => convertTags(paramTags) } || undefined)
            if (!response || response.length == 0) {
                doFetchDashboard(selectedTest.id)
            }
        }, error => dispatch(alertAction("TAGS_FETCH", "Failed to fetch test tags", error)))
    }, [selectedTest])
    useEffect(() => {
        if (currentTags) {
            history.replace(history.location.pathname + "?test=" + selectedTest + "&tags=" + currentTags)
            if (selectedTest) {
                doFetchDashboard(selectedTest.id, currentTags.toString())
            }
        }
    }, [currentTags])

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
                { selectedTest && availableTags && availableTags.length > 0 &&
                <Select
                    isOpen={tagMenuOpen}
                    onToggle={ setTagMenuOpen }
                    selections={currentTags}
                    onSelect={(_, item) => {
                        console.log(availableTags)
                        console.log(item)
                        setCurrentTags(item)
                        setTagMenuOpen(false)
                    }}
                    placeholderText="Choose tags..."
                >{ availableTags.map((tags: any, i: number) => {
                    return (<SelectOption
                            key={i}
                            value={ { ...tags, toString: () => convertTags(tags) } }
                    />) })
                }</Select>
                }
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