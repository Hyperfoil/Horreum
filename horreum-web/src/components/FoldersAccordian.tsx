import {useContext, useEffect, useState} from "react"

import { Accordion, AccordionItem, AccordionContent, AccordionToggle } from '@patternfly/react-core';

import { TreeViewDataItem } from "@patternfly/react-core"

import {fetchFolders} from "../api";
import {AppContext} from "../context/AppContext";
import {AppContextType} from "../context/@types/appContextTypes";
import {AuthBridgeContext} from "../context/AuthBridgeContext";
import {AuthContextType} from "../context/@types/authContextTypes";

type FoldersAccordianProps = {
    folder: string
    onChange(folder: string): void
}

export default function FoldersAccordian(props: FoldersAccordianProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const { teams } = useContext(AuthBridgeContext) as AuthContextType;
    const [folders, setFolders] = useState<string[]>([])
    const [expanded, setExpanded] = useState('')

    useEffect(() => {
        fetchFolders(alerting).then(setFolders)
    }, [teams])

    const root: TreeViewDataItem = { name: "Horreum", children: [] }
    for (const folder of folders) {
        if (!folder) continue
        const parts = folder.split("/")
        let node: TreeViewDataItem = root
        for (const part of parts) {
            const sub = node.children?.find(i => i.name === part)
            if (sub) {
                node = sub
            } else {
                if (!node.children) {
                    node.children = []
                }
                node.children.push({
                    name: part,
                    id: node.id ? node.id + "/" + part : part,
                })
            }
        }
    }
    let activeItem: TreeViewDataItem | undefined = root
    root.defaultExpanded = false
    if (props.folder) {
        for (const part of props.folder.split("/")) {
            activeItem = activeItem?.children?.find(i => i.name === part)
            if (activeItem) {
                activeItem.defaultExpanded = true
            }
        }
    }
    const onToggle = ({id}: { id: any }) => {
        if (id === expanded) {
            setExpanded('');
        } else {
            setExpanded(id);
        }
    };

    return (
        <Accordion asDefinitionList>
            <AccordionItem isExpanded={expanded ==='ex-horreum'}>
                <AccordionToggle
                    onClick={() => {onToggle({id: 'ex-horreum'})}}
                    id="ex-horreum"
                >
                    horreum
                </AccordionToggle>
                <AccordionContent id="ex-horreum-content">
                    <p>
                        Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et
                        dolore magna aliqua.
                    </p>
                </AccordionContent>
            </AccordionItem>
            <AccordionItem isExpanded={expanded === 'ex-quarkus'}>
                <AccordionToggle
                    onClick={() => {onToggle({id: 'ex-quarkus'})}}
                    id="ex-quarkus"
                >
                    quarkus
                </AccordionToggle>
                <AccordionContent
                    id="ex-quarkus-content"
                >
                    <p>
                        Vivamus et tortor sed arcu congue vehicula eget et diam. Praesent nec dictum lorem. Aliquam id diam
                        ultrices, faucibus erat id, maximus nunc.
                    </p>
                </AccordionContent>
            </AccordionItem>
        </Accordion>
    )
/*
        <TreeView
    data={[root]}
    activeItems={activeItem ? [activeItem] : []}
    onSelect={(_, item) => {
        props.onChange(item.id || "")
    }}
    icon={<FolderIcon />}
    expandedIcon={<FolderOpenIcon />}
    />
*/

}
