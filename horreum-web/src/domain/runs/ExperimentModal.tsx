import React, {useState, useEffect, useContext} from "react"

import { Bullseye, Modal, Spinner, Tab, Tabs } from "@patternfly/react-core"
import { TableComposable, Tbody, Td, Th, Thead, Tr } from "@patternfly/react-table"

import { NavLink } from "react-router-dom"
import {experimentApi, ExperimentResult} from "../../api"
import { LogLevelIcon } from "../../components/LogModal"
import { interleave } from "../../utils"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";


type ExperimentModalProps = {
    isOpen: boolean
    onClose(): void
    datasetId: number
}

export default function ExperimentModal(props: ExperimentModalProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [results, setResults] = useState<ExperimentResult[]>()
    const [activeTab, setActiveTab] = useState<string | number>()
    const [activeSecondaryTab, setActiveSecondaryTab] = useState<string | number>("results")
    useEffect(() => {
        if (props.isOpen && !results) {
            experimentApi.runExperiments(props.datasetId).then(
                results => {
                    setResults(results)
                    if (results.length > 0 && results[0].profile) {
                        setActiveTab(results[0].profile.id)
                    }
                },
                error => {
                    alerting.dispatchError( error, "FETCH_EXPERIMENT_EVAL", "Cannot evaluate experiment")
                    props.onClose() // otherwise the error would be hidden
                }
            )
        }
    }, [props.datasetId, props.isOpen])
    return (
        <Modal
            variant="large"
            title="Experiment evaluation"
            isOpen={props.isOpen}
            onClose={props.onClose}
            showClose={true}
        >
            {!results && (
                <Bullseye>
                    <Spinner size="xl" />
                </Bullseye>
            )}
            <Tabs activeKey={activeTab} onSelect={(_, key) => setActiveTab(key)}>
                {results &&
                    results.map(result => (
                        <Tab
                            title={result.profile?.name || "No profile"}
                            key={result.profile?.id || 0}
                            eventKey={result.profile?.id || 0}
                        >
                            <div style={{ overflowY: "auto" }}>
                                <Tabs
                                    isSecondary={true}
                                    activeKey={activeSecondaryTab}
                                    onSelect={(_, key) => setActiveSecondaryTab(key)}
                                >
                                    <Tab eventKey="results" title="Results">
                                        <TableComposable variant="compact">
                                            <Thead>
                                                <Tr>
                                                    <Th>Variable</Th>
                                                    <Th>Experiment value</Th>
                                                    <Th>Baseline value</Th>
                                                    <Th>Result</Th>
                                                </Tr>
                                            </Thead>
                                            <Tbody>
                                                {result.results &&
                                                    Object.entries(result.results).map(([name, r], i) => {
                                                        let style = undefined
                                                        switch (r.overall) {
                                                            case "BETTER":
                                                                style = {
                                                                    backgroundColor:
                                                                        "var(--pf-global--palette--green-100)",
                                                                }
                                                                break
                                                            case "WORSE":
                                                                style = {
                                                                    backgroundColor:
                                                                        "var(--pf-global--palette--red-50)",
                                                                }
                                                                break
                                                            default:
                                                                break
                                                        }
                                                        return (
                                                            <Tr style={style} key={i}>
                                                                <Td>{name}</Td>
                                                                <Td>{r.experimentValue}</Td>
                                                                <Td>{r.baselineValue}</Td>
                                                                <Td>{r.result}</Td>
                                                            </Tr>
                                                        )
                                                    })}
                                            </Tbody>
                                        </TableComposable>
                                    </Tab>
                                    <Tab title="Logs" eventKey="logs">
                                        <TableComposable variant="compact">
                                            <Thead>
                                                <Tr>
                                                    <Th>Level</Th>
                                                    <Th>Dataset</Th>
                                                    <Th>Message</Th>
                                                </Tr>
                                            </Thead>
                                            <Tbody>
                                                {result.logs &&
                                                    result.logs.map((log, i) => (
                                                        <Tr key={i}>
                                                            <Td>
                                                                <LogLevelIcon level={log.level} />
                                                            </Td>
                                                            <Td>
                                                                {log.datasetId === props.datasetId ? (
                                                                    "<this>"
                                                                ) : (
                                                                    <NavLink
                                                                        to={`/run/${log.runId}#dataset${log.datasetOrdinal}`}
                                                                    >
                                                                        {log.runId}/{log.datasetOrdinal + 1}
                                                                    </NavLink>
                                                                )}
                                                            </Td>
                                                            <Td>
                                                                <div
                                                                    dangerouslySetInnerHTML={{ __html: log.message }}
                                                                />
                                                            </Td>
                                                        </Tr>
                                                    ))}
                                            </Tbody>
                                        </TableComposable>
                                    </Tab>
                                </Tabs>
                                <h3>Baseline</h3>
                                {result.baseline &&
                                    interleave(
                                        result.baseline.map((ds, i) => (
                                            <NavLink key={2 * i} to={`/run/${ds.runId}#dataset${ds.ordinal}`}>
                                                {ds.runId}/{ds.ordinal + 1}
                                            </NavLink>
                                        )),
                                        i => <React.Fragment key={2 * i + 1}>, </React.Fragment>
                                    )}
                            </div>
                        </Tab>
                    ))}
            </Tabs>
        </Modal>
    )
}
