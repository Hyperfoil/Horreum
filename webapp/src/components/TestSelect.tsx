import React, { useState, useEffect } from 'react';

import {
    Select,
    SelectOption,
    SelectOptionObject,
} from '@patternfly/react-core';

import { useDispatch, useSelector } from 'react-redux'

import { Test } from '../domain/tests/reducers'
import { all } from '../domain/tests/selectors'
import { fetchSummary } from '../domain/tests/actions'

export interface SelectedTest extends SelectOptionObject {
    id: number,
}

type TestSelectProps = {
    selection: SelectedTest,
    onSelect(selection: SelectedTest): void,
    extraOptions?: SelectedTest[],
    direction?: "up" | "down",
}

export default ({ selection, onSelect, extraOptions, direction } : TestSelectProps) => {
    const [open, setOpen] = useState(false)
    const tests = useSelector(all)
    const dispatch = useDispatch()
    useEffect(() => {
        dispatch(fetchSummary())
    }, [])
    return (<Select
        isOpen={open}
        onToggle={ setOpen }
        selections={selection}
        onSelect={(event, item) => {
            onSelect(item as SelectedTest)
            setOpen(false)
        }}
        direction={direction}
    >{ [
         ...(extraOptions?.map((option, i) => <SelectOption key={i} value={option}/>)),
         ...(tests ? tests.map((test: Test, i: number) => <SelectOption key={i} value={ { id: test.id, toString: () => test.name } as SelectedTest} />) : [])
    ] }</Select>)
}