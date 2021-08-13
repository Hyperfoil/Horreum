import { useState, useEffect } from 'react';
import { useSelector } from 'react-redux'
import { teamsSelector } from '../auth'

import {
    Select,
    SelectOption,
    SelectOptionObject,
} from '@patternfly/react-core';

import { useDispatch } from 'react-redux'
import { alertAction } from '../alerts'

import { fetchTags } from '../domain/tests/api'

const ALL_TAGS = "<all tags>"

export function convertTags(tags: any): string {
    if (!tags) {
        return ALL_TAGS;
    } else if (Object.keys(tags).length === 0) {
        return "<no tags>"
    }
    let str = ""
    for (let [key, value] of Object.entries(tags)) {
        if (str !== "") {
            str = str + ";"
        }
        if (typeof value === "object") {
            // Use the same format as Postgres
            value = JSON.stringify(value).replaceAll(",", ", ").replaceAll(":", ": ");
        }
        str = str + key + ":" + value
    }
    return str
}

export type SelectedTags = SelectOptionObject | null

type TagsSelectProps = {
    testId?: number,
    disabled?: boolean,
    tagFilter?: (tags: SelectedTags) => boolean,
    selection?: SelectedTags,
    onSelect(selection: SelectedTags): void,
    direction?: "up" | "down",
    showIfNoTags?: boolean,
    addAllTagsOption?: boolean,
    beforeTagsLoading?(): void,
    onTagsLoaded?(tags: any[] | undefined): void,
    includeTrashed?: boolean
}

export default function TagsSelect(props: TagsSelectProps) {
    const [open, setOpen] = useState(false)
    const [availableTags, setAvailableTags] = useState<any[]>([])

    const dispatch = useDispatch()
    const testId = props.testId
    const onTagsLoaded = props.onTagsLoaded
    const onSelect = props.onSelect
    const teams = useSelector(teamsSelector)
    useEffect(() => {
        if (!testId) {
            return;
        }
        if (props.beforeTagsLoading) {
            props.beforeTagsLoading()
        }
        fetchTags(testId, props.includeTrashed || false).then((response: any[]) => {
            setAvailableTags(response)
            if (onTagsLoaded) {
                onTagsLoaded(response)
            }
            if (!props.addAllTagsOption && response && response.length === 1) {
                onSelect({ ...response[0], toString: () => convertTags(response[0]) })
            }
        }, error => dispatch(alertAction("TAGS_FETCH", "Failed to fetch test tags", error)))
    }, [testId, onTagsLoaded, props.beforeTagsLoading, onSelect, dispatch, teams, props.includeTrashed, props.addAllTagsOption])
    let options = []
    let hasAllTags = false
    if (props.addAllTagsOption && (!props.tagFilter || props.tagFilter(null))) {
        options.push(ALL_TAGS)
        hasAllTags = true
    }
    const filtered = props.tagFilter ? availableTags.filter(props.tagFilter) : availableTags;
    filtered.map(t => ({ ...t, toString: () => convertTags(t) }))
        .forEach(o => options.push(o))

    const empty = (!filtered || filtered.length === 0) && !hasAllTags
    if (empty && !props.showIfNoTags) {
        return <></>
    }
    return (<Select
            isDisabled={props.disabled || empty}
            isOpen={open}
            onToggle={ setOpen }
            selections={props.selection === null ? ALL_TAGS : props.selection}
            onSelect={(_, item) => {
                props.onSelect(item === ALL_TAGS ? null : item)
                setOpen(false)
            }}
            direction={props.direction}
            menuAppendTo="parent"
            placeholderText="Choose tags..."
        >
            { options.map((tags: SelectOptionObject | string, i: number) => (<SelectOption key={i} value={ tags } />)) }
        </Select>)
}