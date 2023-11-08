import {CSSProperties, useContext, useEffect, useMemo, useState} from "react"

import { Select, SelectGroup, SelectOption, SelectOptionObject, Split, SplitItem } from "@patternfly/react-core"

import {fetchTests, Test} from "../api"
import {AppContext} from "../context/appContext";
import {AppContextType} from "../context/@types/appContextTypes";
import {useSelector} from "react-redux";
import {teamsSelector} from "../auth";

export interface SelectedTest extends SelectOptionObject {
    id: number
    owner?: string
}

type TestSelectProps = {
    tests?: Test[]
    selection?: SelectedTest
    // always use "" for root folder here
    onSelect(test: SelectedTest | undefined, folder: string | undefined, isInitial: boolean): void
    extraOptions?: SelectedTest[]
    direction?: "up" | "down"
    initialTestName?: string
    isDisabled?: boolean
    style?: CSSProperties
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
    const { alerting } = useContext(AppContext) as AppContextType;
    const teams = useSelector(teamsSelector)
    const [testList, setTestList] = useState<Test[]>()
    useMemo(() => {
        fetchTests(alerting, undefined, "*")
            .then(setTestList)
    }, [teams]   )
    // a new instance of test list is created in every invocation => we need shallowEqual
    useEffect(() => {
        if (props.initialTestName && testList) {
            const initialTest = testList.find(t => t.name === props.initialTestName)
            if (initialTest) {
                props.onSelect(
                    { id: initialTest.id, owner: initialTest.owner, toString: () => initialTest.name },
                    initialTest.folder,
                    true
                )
            }
        }
    }, [props.initialTestName, testList, props.onSelect])
    if (testList && testList.length < 16) {
        return <FewTestsSelect {...props} tests={testList} />
    } else {
        return <ManyTestsSelect {...props} tests={testList}  />
    }
}

const ROOT_FOLDER = "Horreum"

function FewTestsSelect(props: TestSelectProps) {
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
            style={props.style}
            width={props.style?.width || "auto"}
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

function ManyTestsSelect(props: TestSelectProps) {
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
        <Split style={props.style}>
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
