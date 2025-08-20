import {useContext, useEffect, useState} from "react"
import {useSelector} from "react-redux"

import {teamsSelector} from "../auth"
import {AppContext} from "../context/appContext";
import {AppContextType} from "../context/@types/appContextTypes";
import {fetchFolders} from "../api";
import {SimpleSelect, SimpleSelectOption, TypeaheadSelect, TypeaheadSelectOption} from "@patternfly/react-templates";

type FolderSelectProps = {
    folder: string
    onChange(folder: string): void
    canCreate: boolean
    readOnly: boolean
    placeHolder: string
}

export default function FolderSelect({folder, onChange, canCreate, readOnly, placeHolder}: FolderSelectProps) {
    const {alerting} = useContext(AppContext) as AppContextType
    const [simpleOptions, setSimpleOptions] = useState<SimpleSelectOption[]>([])
    const [typeaheadOptions, setTypeaheadOptions] = useState<TypeaheadSelectOption[]>([])
    const teams = useSelector(teamsSelector)
    useEffect(() => {
        fetchFolders(alerting).then(folders => {
            // the root folder is represented by a null object in the returned list
            // remove from TypeaheadOptions as its values must be "truthy" -- root folder is handled by the clear button instead
            const opts = folders.filter(f => f || !canCreate).map((f, i) => ({key: i, value: f, content: f}))
            canCreate ? setTypeaheadOptions(opts) : setSimpleOptions(opts)
        })
    }, [teams])
    return (
        canCreate ?
            <TypeaheadSelect
                initialOptions={typeaheadOptions}
                selected={folder}
                onSelect={(_, item) => {
                    if (typeaheadOptions.every((o) => o.value !== item)) { // a new item has just been created!
                        setTypeaheadOptions([...typeaheadOptions, {content: item, value: item}]);
                    }
                    onChange(item as string)
                }}
                isCreatable
                onClearSelection={() => onChange("")}
                placeholder={placeHolder}
                isDisabled={readOnly}
            />
            :
            <SimpleSelect
                initialOptions={simpleOptions}
                selected={folder}
                placeholder={placeHolder}
                isDisabled={readOnly}
                onSelect={(_, item) => onChange(item as string)}
            />
    )
}
