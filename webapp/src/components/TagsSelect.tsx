import React, { useState, useEffect } from 'react';

import {
    Select,
    SelectOption,
    SelectOptionObject,
} from '@patternfly/react-core';

import { useDispatch } from 'react-redux'
import { alertAction } from '../alerts'

import { fetchTags } from '../domain/alerting/api'


export function convertTags(tags: any): string {
    if (!tags || Object.keys(tags).length === 0) {
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

type TagsSelectProps = {
    testId?: number,
    disabled?: boolean,
    initialTags?: string,
    tagFilter?: (tags: any) => boolean,
    selection?: SelectOptionObject,
    onSelect(selection: SelectOptionObject): void,
    direction?: "up" | "down",
    showIfNoTags?: boolean,
    addAllTagsOption?: boolean,
}

export default ({ testId, disabled, initialTags, tagFilter, selection, onSelect, direction, showIfNoTags, addAllTagsOption } : TagsSelectProps) => {
    const [open, setOpen] = useState(false)
    const [availableTags, setAvailableTags] = useState<any[]>([])

    const dispatch = useDispatch()
    useEffect(() => {
        if (!testId) {
            return;
        }
        fetchTags(testId).then((response: any[]) => {
            setAvailableTags(response)
            const tagsString = initialTags && response.find(t => convertTags(t) === initialTags)
            onSelect(tagsString && { ...tagsString, toString: () => convertTags(tagsString) } || undefined)
        }, error => dispatch(alertAction("TAGS_FETCH", "Failed to fetch test tags", error)))
    }, [testId])
    let options = []
    let hasAllTags = false
    if (addAllTagsOption && (!tagFilter || tagFilter({}))) {
        options.push({ toString: () => "<all tags>" })
        hasAllTags = true
    }
    const filtered = tagFilter ? availableTags.filter(tagFilter) : availableTags;
    filtered.map(tags => ({ ...tags, toString: () => convertTags(tags) }))
        .forEach(o => options.push(o))

    const empty = (!filtered || filtered.length == 0) && !hasAllTags
    if (empty && !showIfNoTags) {
        return <></>
    }
    return (<Select
            isDisabled={disabled || empty}
            isOpen={open}
            onToggle={ setOpen }
            selections={selection}
            onSelect={(_, item) => {
                onSelect(item)
                setOpen(false)
            }}
            direction={direction}
            placeholderText="Choose tags..."
        >
            { options.map((tags: any, i: number) => (<SelectOption key={i} value={ tags } />)) }
        </Select>)
}