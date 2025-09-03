import { useState, ReactNode } from "react"

import {
    Bullseye,
    Button,
    EmptyState,
    EmptyStateBody,
    EmptyStateFooter,
    Flex,
    FlexItem,
    Form,
    SimpleList,
    SimpleListItem,
    Spinner,
    Split,
    SplitItem,
    } from "@patternfly/react-core"
import { PlusCircleIcon } from "@patternfly/react-icons"

import ConfirmDeleteModal from "./ConfirmDeleteModal"

interface Item {
    id: number
    name?: string
}

type SplitFormProps<I extends Item> = {
    itemType: string
    newItem(newId: number): I
    canAddItem: boolean
    addItemText: string
    noItemTitle: string
    noItemText: string
    canDelete: boolean | ((item: I) => boolean)
    confirmDelete?(item: I): boolean
    onDelete(item: I): void
    children: ReactNode
    items: I[]
    onChange(items: I[]): void
    selected?: I
    onSelected(item?: I): void
    loading: boolean
    actions?: ReactNode[] | ReactNode
    deleteButtonText?: string
}

export default function SplitForm<I extends Item>(props: SplitFormProps<I>) {
    const [deleteOpen, setDeleteOpen] = useState(false)

    const addItem = () => {
        const newItem = props.newItem(Math.min(...props.items.map(l => l.id - 1), -1))
        props.onChange([...(props.items || []), newItem])
        props.onSelected(newItem)
    }
    if (props.loading) {
        return (
            <Bullseye>
                <Spinner size="xl" />
            </Bullseye>
        )
    }
    if (props.items.length === 0) {
        return (
            <Bullseye>
                <EmptyState titleText={props.noItemTitle} headingLevel="h3">
                    <EmptyStateBody>{props.noItemText}</EmptyStateBody><EmptyStateFooter>
                    {props.canAddItem && <Button onClick={addItem}>{props.addItemText}</Button>}
                </EmptyStateFooter></EmptyState>
            </Bullseye>
        )
    }
    const actions = !props.actions
        ? []
        : Array.isArray(props.actions)
        ? (props.actions as ReactNode[])
        : [props.actions as ReactNode]
    const canDelete =
        typeof props.canDelete === "boolean" ? props.canDelete : props.selected && props.canDelete(props.selected)

    function doDelete() {
        const item = props.items.find(i => i.id === props.selected?.id)
        if (item !== undefined) {
            const newItems = props.items.filter(t => t.id !== props.selected?.id)
            props.onDelete(item)
            props.onChange(newItems)
            props.onSelected(newItems.length > 0 && newItems[0] || undefined)
        }
    }
    return (
        <Split hasGutter>
            <SplitItem style={{ minWidth: "20vw", maxWidth: "20vw", overflow: "clip" }}>
                {props.items.length > 0 && (
                    <SimpleList
                        onSelect={(_, itemProps) => props.onSelected(props.items.find(v => v.id === itemProps.itemId))}
                        isControlled={false}
                    >
                        {props.items.map((t, i) => (
                            <SimpleListItem key={i} itemId={t.id} isActive={props.selected?.id === t.id}>
                                {t.name || <span style={{ color: "#888" }}>(please set the name)</span>}
                            </SimpleListItem>
                        ))}
                    </SimpleList>
                )}
                {props.canAddItem && (
                    <Button icon={<PlusCircleIcon/>} variant="link" onClick={addItem}>
                        {props.addItemText}
                    </Button>
                )}
            </SplitItem>
            <SplitItem isFilled>
                <Flex style={{ marginBottom: "16px" }} justifyContent={{ default: "justifyContentFlexEnd" }}>
                    {actions.map((action, i) => (
                        <FlexItem key={i}>{action}</FlexItem>
                    ))}
                    {canDelete && (
                        <FlexItem>
                            <Button
                                variant="danger"
                                onClick={() => {
                                    const item = props.items.find(i => i.id === props.selected?.id)
                                    if (props.confirmDelete === undefined || (item && props.confirmDelete(item))) {
                                        setDeleteOpen(true)
                                    } else {
                                        doDelete()
                                    }
                                }}
                            >
                                {props.deleteButtonText ?? "Delete"}
                            </Button>
                            <ConfirmDeleteModal
                                isOpen={deleteOpen}
                                onClose={() => setDeleteOpen(false)}
                                onDelete={() => {
                                    doDelete()
                                    return Promise.resolve()
                                }}
                                deleteButtonText={props.deleteButtonText}
                                description={`${props.itemType} ${(props.selected || props.items[0]).name}`}
                            />
                        </FlexItem>
                    )}
                </Flex>
                <Form isHorizontal>{props.children}</Form>
            </SplitItem>
        </Split>
    )
}
