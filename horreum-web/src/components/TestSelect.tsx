import {CSSProperties, Ref, useContext, useEffect, useMemo, useState} from "react"
import {
    MenuToggle,
    MenuToggleElement,
    Select,
    SelectGroup,
    SelectList,
    SelectOption,
    Split,
    SplitItem
} from '@patternfly/react-core';
import {fetchTests, Test} from "../api"
import {AppContext} from "../context/appContext";
import {AppContextType} from "../context/@types/appContextTypes";
import {useSelector} from "react-redux";
import {teamsSelector} from "../auth";
import { SimpleSelect } from "./templates/SimpleSelect";

export interface SelectedTest {
    id: number
    name: string
    owner?: string
    folder?: string
    toString: () => string
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
                    { id: initialTest.id, name: initialTest.name, owner: initialTest.owner, folder: initialTest.folder, toString: () => initialTest.name },
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
    const [selected, setSelected] = useState<string | undefined>();
    const groupedTests = useMemo(() => groupByFolder(props.tests), [props.tests])

    const toggle = (toggleRef: Ref<MenuToggleElement>) =>
        <MenuToggle
            ref={toggleRef}
            isFullWidth
            isExpanded={open}
            isDisabled={props.isDisabled}
            onClick={() => setOpen(!open)}
        >
            {selected}
        </MenuToggle>

    return (
        <Select
            id="test-select"
            isOpen={open}
            selected={selected}
            onSelect={(_, item) => {
                const actualTest = props.tests?.find(t => t.id === item)
                // if this is extra option => folder === undefined
                // if this is test we'll force "" for root folder
                const folder = actualTest === undefined ? undefined : actualTest.folder || ""
                props.onSelect(actualTest, folder || "", false)
                setSelected(actualTest?.name || props.extraOptions?.find(o => o.id === item)?.toString())
                setOpen(false)
            }}
            onOpenChange={(open) => setOpen(open)}
            toggle={toggle}
            placeholder={"Select a test..."}
            isScrollable
            maxMenuHeight="45vh"
            shouldFocusToggleOnSelect
            popperProps={{enableFlip: false, preventOverflow: true}}
            style={props.style}
        >
            {props.extraOptions ?
                <SelectList>
                    {props.extraOptions?.map(o => <SelectOption key={o.id} value={o.id}>{o.toString()}</SelectOption>)}
                </SelectList>
                :
                <></>
            }
            {groupedTests.map((g, i) =>
                <SelectGroup key={i} label={g[0].folder || ROOT_FOLDER}>
                    <SelectList>
                        {g.map(t => <SelectOption key={t.id} value={t.id}>{t.name}</SelectOption>)}
                    </SelectList>
                </SelectGroup>
            )}
        </Select>
    )
}

function ManyTestsSelect(props: TestSelectProps) {
    const [selectedFolder, setSelectedFolder] = useState<string>()
    const [selectedExtra, setSelectedExtra] = useState<SelectedTest>()
    const [selectedTest, setSelectedTest] = useState<number | undefined>();
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

    const options = [
        ...props.extraOptions
            ? props.extraOptions.map(o => ({value: o.id, content: o.toString(), selected: o.id === selectedExtra?.id}))
            : [],
        ...folders.map(f => ({value: f || ROOT_FOLDER, content: f, selected: f === selectedFolder})),
    ]

    return (
        <Split style={props.style}>
            <SplitItem>
                <SimpleSelect
                    initialOptions={options}
                    onSelect={(_, item) => {
                        setSelectedExtra(props.extraOptions?.find(o => o.id === item))
                        setSelectedFolder(selectedExtra ? undefined : item as string)
                        props.onSelect(selectedExtra, selectedFolder === ROOT_FOLDER ? "" : selectedFolder, false)
                    }}
                    selected={props.selection?.owner}
                    placeholder={"Select a folder..."}
                    isScrollable
                    maxMenuHeight="45vh"
                    popperProps={{enableFlip: false, preventOverflow: true}}
                />
            </SplitItem>
            <SplitItem isFilled>
                <SimpleSelect
                    initialOptions={
                        props.tests?.filter(t => t.folder ? t.folder === selectedFolder : selectedFolder === ROOT_FOLDER)
                            .map(t => ({value: t.id, content: t.name, selected: t.id === selectedTest}))
                    }
                    onSelect={(_, item) => {
                        const test = props.tests?.find(t => t.id == item)
                        setSelectedTest(test?.id)
                        props.onSelect(test, selectedFolder || "", false)
                    }}
                    selected={selectedExtra ? undefined : selectedFolder}
                    placeholder={"Select a test..."}
                    isDisabled={props.isDisabled || selectedFolder === undefined || selectedExtra !== undefined}
                    isScrollable
                    maxMenuHeight="45vh"
                    toggleWidth="100%"
                    popperProps={{enableFlip: false, preventOverflow: true}}
                />
            </SplitItem>
        </Split>
    )
}
