import { useState, useEffect } from "react"
import { useDispatch } from "react-redux"

import { Bullseye, FormGroup, Popover, Spinner, TextInput } from "@patternfly/react-core"

import { durationToMillis, millisToDuration } from "../../utils"

import HelpButton from "../../components/HelpButton"
import Labels from "../../components/Labels"
import OptionalFunction from "../../components/OptionalFunction"
import SplitForm from "../../components/SplitForm"
import { TabFunctionsRef } from "../../components/SavedTabs"

import { Test } from "./reducers"
import { useTester } from "../../auth"
import { dispatchError } from "../../alerts"
import { MissingDataRule } from "../alerting/types"
import * as api from "../alerting/api"

type MissingDataNotificationsProps = {
    test?: Test
    funcsRef: TabFunctionsRef
    onModified(modified: boolean): void
}

function compareRules(r1: MissingDataRule, r2: MissingDataRule) {
    const res = (r1.name || "").localeCompare(r2.name || "")
    return res !== 0 ? res : r2.id - r1.id
}

function asWarning(text: string) {
    return <span style={{ color: "var(--pf-global--warning-color--200)" }}>{text}</span>
}

export default function MissingDataNotifications(props: MissingDataNotificationsProps) {
    const [rules, setRules] = useState<MissingDataRule[]>()
    const [deleted, setDeleted] = useState<MissingDataRule[]>([])
    const [selectedRule, setSelectedRule] = useState<MissingDataRule>()
    const [resetCounter, setResetCounter] = useState(0)

    const dispatch = useDispatch()
    useEffect(() => {
        if (!props.test) {
            return
        }
        api.fetchMissingDataRules(props.test.id).then(
            rules => {
                if (rules) {
                    rules.forEach(r => (r.maxStalenessStr = millisToDuration(r.maxStaleness)))
                    setRules(rules.sort(compareRules))
                    if (rules.length > 0) setSelectedRule(rules[0])
                } else {
                    dispatchError(dispatch, "", "FETCH_MISSING_DATA_RULES", "Unexpected value returned from server")
                }
            },
            error => dispatchError(dispatch, error, "FETCH_MISSING_DATA_RULES", "Failed to fetch missing data rules.")
        )
    }, [props.test, resetCounter])

    const isTester = useTester(props.test?.owner)
    if (!props.test || !rules) {
        return (
            <Bullseye>
                <Spinner size="xl" />
            </Bullseye>
        )
    }
    const update = (patch: Partial<MissingDataRule>) => {
        if (selectedRule) {
            const updatedRule = { ...selectedRule, ...patch, modified: true }
            setSelectedRule(updatedRule)
            const newRules = rules.filter(r => r.id !== selectedRule.id)
            newRules.push(updatedRule)
            setRules(newRules.sort(compareRules))
            props.onModified(true)
        }
    }
    props.funcsRef.current = {
        save: () =>
            Promise.all([
                ...deleted.map(rule =>
                    api.deleteMissingDataRule(rule).catch(e => {
                        dispatchError(
                            dispatch,
                            e,
                            "DELETE_MISSING_DATA_RULE",
                            "Failed to delete missing data rule " + rule.name
                        )
                        throw e
                    })
                ),
                ...rules
                    .filter(rule => rule.modified)
                    .map(rule =>
                        api.updateMissingDataRule(rule).then(
                            () => {
                                rule.modified = false
                            },
                            e => {
                                dispatchError(
                                    dispatch,
                                    e,
                                    "UPDATE_MISSING_DATA_RULE",
                                    "Failed to update missing data rule " + rule.name
                                )
                            }
                        )
                    ),
            ]).then(() => {
                setDeleted([])
            }),
        reset: () => {
            setRules(undefined)
            setSelectedRule(undefined)
            setDeleted([])
            setResetCounter(resetCounter + 1)
        },
    }
    return (
        <SplitForm
            itemType="Rule"
            newItem={id => ({
                id,
                name: "",
                labels: [],
                maxStaleness: 86_400_000,
                maxStalenessStr: "1d",
                testId: props.test?.id || -1,
                modified: true,
            })}
            canAddItem={isTester}
            addItemText="Add new rule..."
            noItemTitle="No rules defined"
            noItemText="This test does not define any rules for periodic data upload."
            canDelete={isTester}
            onDelete={rule => {
                if (rule.id >= 0) {
                    setDeleted([...deleted, rule])
                }
            }}
            items={rules}
            onChange={rules => {
                setRules(rules.sort(compareRules))
                props.onModified(true)
            }}
            selected={selectedRule}
            onSelected={rule => setSelectedRule(rule)}
            loading={rules === undefined}
        >
            {selectedRule && (
                <>
                    <FormGroup label="Name" fieldId="name">
                        <TextInput
                            isReadOnly={!isTester}
                            value={selectedRule.name || ""}
                            onChange={name => update({ name })}
                        />
                    </FormGroup>
                    <FormGroup
                        label="Labels"
                        fieldId="labels"
                        validated={
                            selectedRule.labels.length > 0 && !selectedRule.condition
                                ? "warning"
                                : selectedRule.labels.length == 0 && selectedRule.condition
                                ? "warning"
                                : "default"
                        }
                        helperText={
                            selectedRule.labels.length > 0 && !selectedRule.condition
                                ? asWarning("Labels are defined but there is no condition evaulating these.")
                                : selectedRule.labels.length == 0 && selectedRule.condition
                                ? asWarning("Condition is used but the labels are not defined.")
                                : undefined
                        }
                    >
                        <Labels
                            isReadOnly={!isTester}
                            labels={selectedRule.labels}
                            onChange={labels => update({ labels })}
                            defaultMetrics={false}
                            defaultFiltering={true}
                        />
                    </FormGroup>
                    <FormGroup
                        label={
                            <>
                                Condition
                                <Popover
                                    headerContent="Condition filtering the datasets"
                                    bodyContent={
                                        <div>
                                            Filtering function takes the label value (in case of single label) or object
                                            keyed by label names (in case of multiple labels) as its only parameter and
                                            returns <code>true</code> if the dataset is a match for this rule.
                                            <br />
                                            When the condition is not present all datasets match; labels are ignored in
                                            this case.
                                        </div>
                                    }
                                >
                                    <HelpButton />
                                </Popover>
                            </>
                        }
                        fieldId="condition"
                    >
                        <OptionalFunction
                            readOnly={!isTester}
                            func={selectedRule.condition}
                            onChange={func => update({ condition: func })}
                            undefinedText="No condition (all datasets match)"
                            addText="Set condition"
                            defaultFunc="value => true"
                        />
                    </FormGroup>
                    <FormGroup
                        label={
                            <>
                                Max staleness
                                <Popover
                                    headerContent="Dataset staleness"
                                    bodyContent={
                                        <div>
                                            You can use a value in milliseconds or suffix the number with <code>s</code>
                                            , <code>m</code>, <code>h</code> or <code>d</code> to denote seconds,
                                            minutes, hours or days, respectively.
                                            <br />A notification is sent when there is no dataset in this test matching
                                            the rule newer than this value, and later after this duration since last
                                            notification.
                                            <br />
                                            All rules are evaluated and send notifications independently.
                                        </div>
                                    }
                                >
                                    <HelpButton />
                                </Popover>
                            </>
                        }
                        helperText="e.g. 1d 2h 3m 4s"
                        fieldId="maxStaleness"
                    >
                        <TextInput
                            value={selectedRule.maxStalenessStr}
                            isRequired
                            type="text"
                            id="maxStaleness"
                            isReadOnly={!isTester}
                            validated={selectedRule.maxStaleness !== undefined ? "default" : "error"}
                            onChange={value =>
                                update({ maxStalenessStr: value, maxStaleness: durationToMillis(value) })
                            }
                        />
                    </FormGroup>
                </>
            )}
        </SplitForm>
    )
}
