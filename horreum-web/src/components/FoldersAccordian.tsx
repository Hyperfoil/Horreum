import {useContext, useEffect, useState} from "react"
import { useDispatch, useSelector } from "react-redux"

import { Accordion, AccordionItem, AccordionContent, AccordionToggle } from '@patternfly/react-core';

import { TreeView, TreeViewDataItem } from "@patternfly/react-core"

import { teamsSelector } from "../auth"
import {fetchFolders} from "../api";
import {AppContext} from "../context/appContext";
import {AppContextType} from "../context/@types/appContextTypes";

type FoldersAccordianProps = {
    folder: string
    onChange(folder: string): void
}

export default function FoldersAccordian(props: FoldersAccordianProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [folders, setFolders] = useState<string[]>([])
    const teams = useSelector(teamsSelector)
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
            <AccordionItem>
                <AccordionToggle
                    onClick={() => {onToggle({id: 'ex-horreum'})}}
                    isExpanded={expanded ==='ex-horreum'}
                    id="ex-horreum"
                >
                    horreum
                </AccordionToggle>
                <AccordionContent
                    id="ex-horreum-content"
                    isHidden={expanded !== 'ex-horreum'}
                >
                    <p>
                        Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et
                        dolore magna aliqua.
                    </p>
                </AccordionContent>
            </AccordionItem>

            <AccordionItem>
                <AccordionToggle
                    onClick={() => {onToggle({id: 'ex-quarkus'})}}
                    isExpanded={expanded === 'ex-quarkus'}
                    id="ex-quarkus"
                >
                    quarkus
                </AccordionToggle>
                <AccordionContent
                    id="ex-quarkus-content"
                    isHidden={expanded !=='ex-quarkus'}
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
