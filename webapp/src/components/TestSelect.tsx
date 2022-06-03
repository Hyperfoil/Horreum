import { useEffect, useMemo, useState } from "react"

import { Select, SelectGroup, SelectOption, SelectOptionObject, Split, SplitItem } from "@patternfly/react-core"

import { useDispatch, useSelector, shallowEqual } from "react-redux"

import { TestDispatch } from "../domain/tests/reducers"
import { Test } from "../api"
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
    // always use "" for root folder here
    onSelect(test: SelectedTest | undefined, folder: string | undefined, isInitial: boolean): void
    extraOptions?: SelectedTest[]
    direction?: "up" | "down"
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

export default function TestSelect(props: TestSelectProps) {
    // a new instance of test list is created in every invocation => we need shallowEqual
    const tests = useSelector(all, shallowEqual)
    const dispatch = useDispatch<TestDispatch>()
    const teams = useSelector(teamsSelector)
    useEffect(() => {
        dispatch(fetchSummary(undefined, "*")).catch(noop)
    }, [dispatch, teams])
    useEffect(() => {
        if (props.initialTestName && tests) {
            const initialTest = tests.find(t => t.name === props.initialTestName)
            if (initialTest) {
                props.onSelect(
                    { id: initialTest.id, owner: initialTest.owner, toString: () => initialTest.name },
                    initialTest.folder,
                    true
                )
            }
        }
    }, [props.initialTestName, tests, props.onSelect])
    if (tests && tests.length < 16) {
        return <FewTestsSelect tests={tests} {...props} />
    } else {
        return <ManyTestsSelect tests={tests} {...props} />
    }
}

type FewTestsSelectProps = {
    tests: Test[] | undefined
} & TestSelectProps

const ROOT_FOLDER = "(root folder)"

function FewTestsSelect(props: FewTestsSelectProps) {
    const [open, setOpen] = useState(false)
    const groupedTests = useMemo(() => groupByFolder(props.tests), [props.tests])
    return (
        <Select
            isOpen={open}
            onToggle={setOpen}
            selections={props.selection}
            menuAppendTo="parent"
            onSelect={(_, item) => {
                const test = item as SelectedTest
                const actualTest = props.tests?.find(t => t.id === test.id)
                // if this is extra option => folder === undefined
                // if this is test we'll force "" for root folder
                const folder = actualTest === undefined ? undefined : actualTest.folder || ""
                props.onSelect(test, folder || "", false)
                setOpen(false)
            }}
            direction={props.direction}
            placeholderText="Select test..."
            hasPlaceholderStyle={true}
            isDisabled={props.isDisabled}
        >
            {[
                ...(props.extraOptions
                    ? props.extraOptions.map((option, i) => <SelectOption key={`extra${i}`} value={option} />)
                    : []),
                ...groupedTests.map((group, i) => (
                    <SelectGroup key={i} label={group[0].folder || ROOT_FOLDER}>
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

type ManyTestsSelectProps = {
    tests: Test[] | undefined
} & TestSelectProps

function ManyTestsSelect(props: ManyTestsSelectProps) {
    const [foldersOpen, setFoldersOpen] = useState(false)
    const [testsOpen, setTestsOpen] = useState(false)
    const [selectedFolder, setSelectedFolder] = useState<string>()
    const [selectedExtra, setSelectedExtra] = useState<SelectedTest>()
    useEffect(() => {
        if (!props.selection || !props.tests) {
            return
        }

        setSelectedFolder(props.tests.find(t => t.id === props.selection?.id)?.folder || ROOT_FOLDER)
    }, [props.selection, props.tests])
    const folders = useMemo(
        () => [ROOT_FOLDER, ...[...new Set((props.tests || []).map(t => t.folder).filter(f => !!f))].sort()],
        [props.tests]
    )
    return (
        <Split>
            <SplitItem>
                <Select
                    isOpen={foldersOpen}
                    onToggle={setFoldersOpen}
                    selections={selectedExtra || selectedFolder}
                    menuAppendTo="parent"
                    onSelect={(_, item) => {
                        if (props.extraOptions?.some(o => o === item)) {
                            const extra = item as SelectedTest
                            setSelectedExtra(extra)
                            setSelectedFolder(undefined)
                            props.onSelect(extra, undefined, false)
                        } else {
                            const folder = item as string
                            setSelectedExtra(undefined)
                            setSelectedFolder(folder)
                            props.onSelect(undefined, folder === ROOT_FOLDER ? "" : folder, false)
                        }
                        setFoldersOpen(false)
                    }}
                    placeholderText="Select folder..."
                    hasPlaceholderStyle={true}
                    direction={props.direction}
                    isDisabled={props.isDisabled}
                >
                    {[
                        ...(props.extraOptions
                            ? props.extraOptions.map((option, i) => <SelectOption key={`extra${i}`} value={option} />)
                            : []),
                        ...folders.map((f, i) => <SelectOption key={i} value={f} />),
                    ]}
                </Select>
            </SplitItem>
            <SplitItem>
                <Select
                    isOpen={testsOpen}
                    onToggle={setTestsOpen}
                    selections={selectedExtra ? undefined : props.selection}
                    menuAppendTo="parent"
                    onSelect={(_, item) => {
                        props.onSelect(item as SelectedTest, selectedFolder || "", false)
                        setTestsOpen(false)
                    }}
                    direction={props.direction}
                    placeholderText="Select test..."
                    hasPlaceholderStyle={true}
                    isDisabled={props.isDisabled || selectedFolder === undefined || selectedExtra !== undefined}
                >
                    {props.tests
                        ?.filter(t => t.folder === selectedFolder || (selectedFolder === ROOT_FOLDER && !t.folder))
                        .map((test, i) => (
                            <SelectOption
                                key={i}
                                value={
                                    {
                                        id: test.id,
                                        owner: test.owner,
                                        toString: () => test.name,
                                    } as SelectedTest
                                }
                            />
                        ))}
                </Select>
            </SplitItem>
        </Split>
    )
}
