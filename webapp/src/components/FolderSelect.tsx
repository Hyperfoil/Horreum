import { useEffect, useState } from "react"
import { useSelector, useDispatch } from "react-redux"

import { Select, SelectOption } from "@patternfly/react-core"

import { teamsSelector } from "../auth"
import { TestDispatch } from "../domain/tests/reducers"
import { fetchFolders } from "../domain/tests/actions"
import { allFolders } from "../domain/tests/selectors"
import { UPDATE_FOLDERS } from "../domain/tests/actionTypes"
import { noop } from "../utils"

type FolderSelectProps = {
    folder: string
    onChange(folder: string): void
    canCreate: boolean
    readOnly: boolean
}

export default function FolderSelect(props: FolderSelectProps) {
    const [open, setOpen] = useState(false)
    const all = useSelector(allFolders())
    const dispatch = useDispatch<TestDispatch>()
    const teams = useSelector(teamsSelector)
    useEffect(() => {
        dispatch(fetchFolders()).catch(noop)
    }, [dispatch, teams])
    return (
        <Select
            readOnly={props.readOnly}
            isOpen={open}
            isCreatable={props.canCreate}
            variant={props.canCreate ? "typeahead" : "single"}
            onToggle={setOpen}
            selections={props.folder}
            menuAppendTo="parent"
            onSelect={(_, item) => {
                props.onChange(item as string)
                setOpen(false)
            }}
            onCreateOption={newFolder => {
                dispatch({ type: UPDATE_FOLDERS, folders: [...all, newFolder].sort() })
                props.onChange(newFolder)
            }}
            placeholderText="Horreum"
        >
            {all.map((folder, i) => (
                <SelectOption key={i} value={folder || ""}>
                    {folder || "Horreum"}
                </SelectOption>
            ))}
        </Select>
    )
}
