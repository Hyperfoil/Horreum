import { SearchInput, ToolbarFilter } from "@patternfly/react-core"
import { useState } from "react";

type FilterSearchInputProps = {
    placeholder: string
    // true  = return the raw SearchInput
    // false = wrap in a ToolbarFilter (this is the default)
    raw?: boolean
    onSearchBy?: (value: string) => void
    onClearBy?: () => void
}

function FilterSearchInput({
    placeholder,
    raw,
    onSearchBy,
    onClearBy
}: FilterSearchInputProps) {
    const [value, setValue] = useState('');
    const onValueChange = (value: string) => {
        setValue(value)
    };

    const onClear = () => {
        setValue("");

        if (onClearBy) {
            onClearBy()
        }
    };

    const onSearch = (event: React.SyntheticEvent<HTMLButtonElement>, value: string) => {
        if (event.nativeEvent instanceof KeyboardEvent && event.type !== "keydown") {
            return
        }
        
        if (onSearchBy) {
            onSearchBy(value)
        }
    };

    const input = (
        <SearchInput
            aria-label="filter"
            placeholder={placeholder}
            value={value}
            onChange={(_event, value) => onValueChange(value)}
            onClear={onClear}
            onSearch={onSearch}
        />
    )

    return (
        <>
            {raw && input || (
                <ToolbarFilter categoryName="Name">
                    {input}
                </ToolbarFilter>
            )}
        </>
    )
}

export default FilterSearchInput