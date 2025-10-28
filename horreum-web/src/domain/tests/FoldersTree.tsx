import {useContext, useEffect, useState} from "react"

import { TreeView, TreeViewDataItem } from "@patternfly/react-core"
import { FolderIcon, FolderOpenIcon } from "@patternfly/react-icons"

import {AppContext} from "../../context/AppContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import {fetchFolders} from "../../api";
import {AuthBridgeContext} from "../../context/AuthBridgeContext";
import {AuthContextType} from "../../context/@types/authContextTypes";

type FoldersTreeProps = {
    folder: string
    onChange(folder: string): void
}

export default function FoldersTree(props: FoldersTreeProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const { teams } = useContext(AuthBridgeContext) as AuthContextType;
    const [folders, setFolders] = useState<string[]>([])
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
    return (
        <TreeView
            data={[root]}
            activeItems={activeItem ? [activeItem] : []}
            onSelect={(_, item) => {
                if (item.id)
                    props.onChange(item.id)
            }}
            icon={<FolderIcon />}
            expandedIcon={<FolderOpenIcon />}
        />
    )
}
