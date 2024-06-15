import {ToolbarFilter} from "@patternfly/react-core";
import React, {useMemo} from "react";
import LabelFilterOption from "./LabelFilter/LabelFilterOption";


interface ToolbarLabelFilterProps {
    name: string;
    options: string[];
    filter: any;
    setFilter(filter: any): any;
    activeMenu: string;
    clearCallback( callback: () => void): any;
}

export default function ToolbarLabelFilter(props: ToolbarLabelFilterProps) {

    const [selection, setSelection] = React.useState<any | undefined>(undefined);

    props.clearCallback(() => setSelection(undefined))

    const selectOptions = LabelFilterOption (
        {
            name: props.name,
            options: props.options,
            filter: props.filter,
            setFilter: props.setFilter,
            selection: selection,
            setSelection: setSelection
        }
    );

    const checkActive = useMemo(
        () => props.name === props.activeMenu,
        [props.activeMenu]
    );

    return (
        <ToolbarFilter
            chips={selection !== undefined ? [selection] : ([] as string[])}
            deleteChip={() => {
                const newFilter = {...props.filter}
                delete newFilter[props.name]
                props.setFilter(newFilter)
                setSelection(undefined);
            }}
            deleteChipGroup={() => {
                const newFilter = {...props.filter}
                delete newFilter[props.name]
                props.setFilter(newFilter)
                setSelection(undefined);
            }}
            categoryName={props.name}
            showToolbarItem={checkActive}
        >
            {selectOptions}
        </ToolbarFilter>

    );
}
