import { useEffect, useState } from "react"
import { useDispatch } from "react-redux"

import {
    Bullseye,
    Button,
    DualListSelector,
    DualListSelectorTreeItemData,
    Flex,
    FlexItem,
    Spinner,
} from "@patternfly/react-core"

import { dispatchError } from "../../alerts"
import { noop } from "../../utils"
import { useTester } from "../../auth"
import { TabFunctionsRef } from "../../components/SavedTabs"
import { Transformer, TransformerInfo, allTransformers } from "../schemas/api"
import { TestDispatch } from "./reducers"
import { updateTransformers } from "./actions"
import TransformationLogModal from "./TransformationLogModal"

type TransformersProps = {
    testId: number
    owner?: string
    originalTransformers: Transformer[]
    updateTransformers(newTransformers: Transformer[]): void
    funcsRef: TabFunctionsRef
}

type SchemaItem = DualListSelectorTreeItemData & {
    children: TransformerItem[]
}
type TransformerItem = DualListSelectorTreeItemData & {
    schemaId: number
    schemaName: string
    schemaUri: string
}

function transformersToTree(ts: TransformerInfo[]): SchemaItem[] {
    const schemaIds = Array.from(new Set(ts.map(t => t.schemaId)))
    return schemaIds.map(id => {
        const list = ts.filter(t => t.schemaId === id)
        const name = `${list[0].schemaName} (${list[0].schemaUri})`
        return {
            id: "S" + id,
            isChecked: false,
            checkProps: { style: { display: "none" }, "aria-label": name },
            hasBadge: true,
            badgeProps: { isRead: true },
            text: name,
            children: list.map(t => ({
                id: t.transformerId.toString(),
                isChecked: false,
                checkProps: { "aria-label": t.transformerName },
                text: t.transformerName,
                schemaId: id,
                schemaUri: t.schemaUri,
                schemaName: t.schemaName,
            })),
        }
    })
}

function selectedTransformers(items: DualListSelectorTreeItemData[]) {
    return items
        .flatMap(s => s.children || [])
        .map(t => parseInt(t.id))
        .sort((a, b) => a - b)
}

function compareTransformers(ts1: number[], ts2: number[]) {
    if (ts1.length != ts2.length) {
        return false
    }
    for (let i = 0; i < ts1.length; ++i) {
        if (ts1[i] !== ts2[i]) {
            return false
        }
    }
    return true
}

function toInfo(t: Transformer) {
    return {
        ...t,
        transformerId: t.id,
        transformerName: t.name,
    }
}

function excludeSelected(tree: SchemaItem[], excluded: Transformer[]) {
    return tree
        .map((s: SchemaItem) => {
            const originalChildren: TransformerItem[] = s.children
            const filteredChildren = originalChildren.filter(t => !excluded.some(ot => ot.id === parseInt(t.id)))
            return {
                ...s,
                children: filteredChildren,
            }
        })
        .filter(s => s.children.length > 0)
}

export default function Transformers(props: TransformersProps) {
    const dispatch = useDispatch<TestDispatch>()
    const [counter, setCounter] = useState(0)
    const [loading, setLoading] = useState(false)
    const [originalOptions, setOriginalOptions] = useState<SchemaItem[]>([])
    const [options, setOptions] = useState<SchemaItem[]>([])
    const [chosen, setChosen] = useState<SchemaItem[]>([])
    const [logModalOpen, setLogModalOpen] = useState(false)
    const isTester = useTester(props.owner)

    useEffect(() => {
        setLoading(true)
        allTransformers()
            .then(
                ts => {
                    const items = transformersToTree(ts)
                    setOriginalOptions(items)
                    setOptions(items)
                    setCounter(counter + 1)
                },
                error =>
                    dispatchError(dispatch, error, "FETCH_TRANSFORMERS", "Failed to fetch all transformers").catch(noop)
            )
            .finally(() => setLoading(false))
    }, [])
    useEffect(() => {
        setOptions(excludeSelected(originalOptions, props.originalTransformers))
        setChosen(transformersToTree(props.originalTransformers.map(toInfo)))
    }, [props.originalTransformers, originalOptions])
    props.funcsRef.current = {
        save: () =>
            dispatch(
                updateTransformers(
                    props.testId,
                    chosen
                        .flatMap(s => s.children || [])
                        .map(t => ({
                            id: parseInt(t.id),
                            name: t.text,
                            schemaId: t.schemaId,
                            schemaUri: t.schemaUri,
                            schemaName: t.schemaName,
                            // unused
                            description: "",
                            extractors: [],
                            owner: "",
                            access: 0,
                        }))
                )
            ),
        reset: () => {
            setOptions(excludeSelected(originalOptions, props.originalTransformers))
            setChosen(transformersToTree(props.originalTransformers.map(toInfo)))
        },
        modified: () =>
            !compareTransformers(
                selectedTransformers(chosen),
                props.originalTransformers.map(t => t.id).sort((a, b) => a - b)
            ),
    }
    if (loading) {
        return (
            <Bullseye>
                <Spinner size="xl" />
            </Bullseye>
        )
    }
    /**
     * This weird trick is needed to overcome Patternfly caching the originalAvaialableOptions in mergedCopy
     * We need to force re-create the component. Better ways are welcome.
     */
    return (
        <>
            <Flex justifyContent={{ default: "justifyContentFlexEnd" }}>
                {isTester && (
                    <FlexItem>
                        <Button onClick={() => setLogModalOpen(true)}>Show transformation log</Button>
                    </FlexItem>
                )}
            </Flex>
            <TransformationLogModal
                testId={props.testId}
                title="Transformations"
                emptyMessage="There are no logs from transformers"
                isOpen={logModalOpen}
                onClose={() => setLogModalOpen(false)}
            />
            {[counter].map(c => (
                <DualListSelector
                    key={c}
                    isSearchable
                    isTree
                    isDisabled={!isTester}
                    availableOptions={options}
                    chosenOptions={chosen}
                    onListChange={(newAvailable, newChosen) => {
                        setOptions(newAvailable as SchemaItem[])
                        setChosen(newChosen as SchemaItem[])
                    }}
                />
            ))}{" "}
        </>
    )
}
