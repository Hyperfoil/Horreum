import { useEffect, useMemo, useState } from "react"

import { Select, SelectGroup, SelectOption, SelectOptionObject } from "@patternfly/react-core"

import { useDispatch, useSelector, shallowEqual } from "react-redux"

import { Test, TestDispatch } from "../domain/tests/reducers"
import { all } from "../domain/tests/selectors"
import { fetchSummary } from "../domain/tests/actions"
import { teamsSelector } from "../auth"
import { noop } from "../utils"

export interface SelectedTest extends SelectOptionObject {
    id: number
    owner?: string
}

type TestSelectProps = {
    selection?: SelectedTest
    onSelect(selection: SelectedTest, isInitial: boolean): void
    extraOptions?: SelectedTest[]
    direction?: "up" | "down"
    placeholderText?: string
    initialTestName?: string
    isDisabled?: boolean
}

function groupByFolder(tests: Test[] | undefined | false) {
    if (!tests) {
        return []
    }
    const groups = tests
        .reduce((groups: Test[][], test) => {
            const group = groups.find(g => g[0].folder === test.folder)
            if (group) {
                group.push(test)
            } else {
                groups.push([test])
            }
            return groups
        }, [])
        .sort((a, b) => (a[0].folder || "").localeCompare(b[0].folder || ""))
    groups.forEach(g => g.sort((a, b) => a.name.localeCompare(b.name)))
    return groups
}

export default function TestSelect({
    selection,
    onSelect,
    extraOptions,
    direction,
    placeholderText,
    initialTestName,
    isDisabled,
}: TestSelectProps) {
    const [open, setOpen] = useState(false)
    // a new instance of test list is created in every invocation => we need shallowEqual
    const tests = useSelector(all, shallowEqual)
    const groupedTests = useMemo(() => groupByFolder(tests), [tests])
    const dispatch = useDispatch<TestDispatch>()
    const teams = useSelector(teamsSelector)
    useEffect(() => {
        dispatch(fetchSummary(undefined, "*")).catch(noop)
    }, [dispatch, teams])
    useEffect(() => {
        if (initialTestName && tests) {
            const initialTest = tests.find(t => t.name === initialTestName)
            if (initialTest) {
                onSelect({ id: initialTest.id, owner: initialTest.owner, toString: () => initialTest.name }, true)
            }
        }
    }, [initialTestName, tests, onSelect])
    return (
        <Select
            isOpen={open}
            onToggle={setOpen}
            selections={selection}
            menuAppendTo="parent"
            onSelect={(_, item) => {
                onSelect(item as SelectedTest, false)
                setOpen(false)
            }}
            direction={direction}
            placeholderText={placeholderText}
            isDisabled={isDisabled}
        >
            {[
                ...(extraOptions ? extraOptions.map((option, i) => <SelectOption key={i} value={option} />) : []),
                ...groupedTests.map((group, i) => (
                    <SelectGroup key={i} label={group[0].folder || "(root folder)"}>
                        {group.map((test: Test, j) => (
                            <SelectOption
                                key={j}
                                value={{ id: test.id, owner: test.owner, toString: () => test.name } as SelectedTest}
                            />
                        ))}
                    </SelectGroup>
                )),
            ]}
        </Select>
    )
}
