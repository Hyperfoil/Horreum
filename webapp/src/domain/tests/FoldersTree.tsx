import { useEffect } from "react"
import { useDispatch, useSelector } from "react-redux"

import { TreeView, TreeViewDataItem } from "@patternfly/react-core"
import { FolderIcon, FolderOpenIcon } from "@patternfly/react-icons"

import { teamsSelector } from "../../auth"
import { noop } from "../../utils"
import { fetchFolders } from "./actions"
import { TestDispatch } from "./reducers"
import { allFolders } from "./selectors"

type FoldersTreeProps = {
    folder: string
    onChange(folder: string): void
}

export default function FoldersTree(props: FoldersTreeProps) {
    const folders = useSelector(allFolders())
    const teams = useSelector(teamsSelector)
    const dispatch = useDispatch<TestDispatch>()
    useEffect(() => {
        dispatch(fetchFolders()).catch(noop)
    }, [teams])
    const root: TreeViewDataItem = { name: "(root)", children: [] }
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
    root.defaultExpanded = true
    if (props.folder) {
        for (const part of props.folder.split("/")) {
            activeItem = activeItem?.children?.find(i => i.name === part)
            if (activeItem) {
                activeItem.defaultExpanded = true
            }
        }
    }
    if (root.children?.length === 0) {
        return null
    }
    return (
        <TreeView
            data={[root]}
            activeItems={activeItem ? [activeItem] : []}
            onSelect={(_, item) => {
                props.onChange(item.id || "")
            }}
            icon={<FolderIcon />}
            expandedIcon={<FolderOpenIcon />}
        />
    )
}
