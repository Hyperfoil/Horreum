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
    onTagsLoaded?(tags: any[] | undefined): void,
}

export default (props: TagsSelectProps) => {
    const [open, setOpen] = useState(false)
    const [availableTags, setAvailableTags] = useState<any[]>([])

    const dispatch = useDispatch()
    useEffect(() => {
        if (props.onTagsLoaded) props.onTagsLoaded(undefined)
        if (!props.testId) {
            return;
        }
        fetchTags(props.testId).then((response: any[]) => {
            setAvailableTags(response)
            let tags: any = undefined
            if (props.initialTags) {
                tags = response.find(t => convertTags(t) === props.initialTags)
            } else if (response.length === 1) {
                tags = response[0]
            }
            props.onSelect(tags && { ...tags, toString: () => convertTags(tags) } || undefined)
            if (props.onTagsLoaded) {
                props.onTagsLoaded(response)
            }
        }, error => dispatch(alertAction("TAGS_FETCH", "Failed to fetch test tags", error)))
    }, [props.testId])
    let options = []
    let hasAllTags = false
    if (props.addAllTagsOption && (!props.tagFilter || props.tagFilter({}))) {
        options.push({ toString: () => "<all tags>" })
        hasAllTags = true
    }
    const filtered = props.tagFilter ? availableTags.filter(props.tagFilter) : availableTags;
    filtered.map(t => ({ ...t, toString: () => convertTags(t) }))
        .forEach(o => options.push(o))

    const empty = (!filtered || filtered.length == 0) && !hasAllTags
    if (empty && !props.showIfNoTags) {
        return <></>
    }
    return (<Select
            isDisabled={props.disabled || empty}
            isOpen={open}
            onToggle={ setOpen }
            selections={props.selection}
            onSelect={(_, item) => {
                props.onSelect(item)
                setOpen(false)
            }}
            direction={props.direction}
            menuAppendTo="parent"
            placeholderText="Choose tags..."
        >
            { options.map((tags: any, i: number) => (<SelectOption key={i} value={ tags } />)) }
        </Select>)
}