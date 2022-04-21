import { useState, ReactNode } from "react"

import {
    Bullseye,
    Button,
    EmptyState,
    EmptyStateBody,
    Form,
    SimpleList,
    SimpleListItem,
    Spinner,
    Split,
    SplitItem,
    Title,
} from "@patternfly/react-core"
import { PlusCircleIcon } from "@patternfly/react-icons"

import ConfirmDeleteModal from "./ConfirmDeleteModal"

interface Item {
    id: number
    name: string
}

type SplitFormProps<I extends Item> = {
    itemType: string
    newItem(newId: number): I
    canAddItem: boolean
    addItemText: string
    noItemTitle: string
    noItemText: string
    canDelete: boolean
    onDelete(item: I): void
    children: ReactNode
    items: I[]
    onChange(items: I[]): void
    selected?: I
    onSelected(item?: I): void
    loading: boolean
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
                <EmptyState>
                    <Title headingLevel="h3">{props.noItemTitle}</Title>
                    <EmptyStateBody>{props.noItemText}</EmptyStateBody>
                    {props.canAddItem && <Button onClick={addItem}>{props.addItemText}</Button>}
                </EmptyState>
            </Bullseye>
        )
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
                    <Button variant="link" onClick={addItem}>
                        <PlusCircleIcon />
                        {"\u00A0"}
                        {props.addItemText}
                    </Button>
                )}
            </SplitItem>
            <SplitItem isFilled>
                {props.canDelete && (
                    <div style={{ textAlign: "right", marginBottom: "16px" }}>
                        <Button variant="danger" onClick={() => setDeleteOpen(true)}>
                            Delete
                        </Button>
                        <ConfirmDeleteModal
                            isOpen={deleteOpen}
                            onClose={() => setDeleteOpen(false)}
                            onDelete={() => {
                                const item = props.items.find(i => i.id === props.selected?.id)
                                if (item !== undefined) {
                                    const newItems = props.items.filter(t => t.id !== props.selected?.id)
                                    props.onDelete(item)
                                    props.onChange(newItems)
                                    props.onSelected(newItems.length > 0 ? newItems[0] : undefined)
                                }
                                return Promise.resolve()
                            }}
                            description={`${props.itemType} ${(props.selected || props.items[0]).name}`}
                        />
                    </div>
                )}
                <Form isHorizontal>{props.children}</Form>
            </SplitItem>
        </Split>
    )
}
