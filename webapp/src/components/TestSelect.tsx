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
import { registerAfterLogin } from '../auth'

export interface SelectedTest extends SelectOptionObject {
    id: number,
    owner?: string,
}

type TestSelectProps = {
    selection?: SelectedTest,
    onSelect(selection: SelectedTest): void,
    extraOptions?: SelectedTest[],
    direction?: "up" | "down",
    placeholderText?: string,
    initialTestName?: string
}

export default ({ selection, onSelect, extraOptions, direction, placeholderText, initialTestName } : TestSelectProps) => {
    const [open, setOpen] = useState(false)
    const tests = useSelector(all)
    const dispatch = useDispatch()
    useEffect(() => {
        dispatch(fetchSummary())
        dispatch(registerAfterLogin("reload_tests", () => {
            dispatch(fetchSummary())
        }))
    }, [])
    useEffect(() => {
        if (initialTestName && tests) {
            const initialTest = tests.find(t => t.name === initialTestName)
            if (initialTest && initialTestName !== selection?.toString()) {
                onSelect({ id: initialTest.id, toString: () => initialTest.name })
            }
        }
    }, [initialTestName, tests]);
    return (<Select
        isOpen={open}
        onToggle={ setOpen }
        selections={selection}
        onSelect={(_, item) => {
            onSelect(item as SelectedTest)
            setOpen(false)
        }}
        direction={direction}
        placeholderText={placeholderText}
    >{ [
         ...(extraOptions ? extraOptions.map((option, i) => <SelectOption key={i} value={option}/>) : []),
         ...(tests ? tests.map((test: Test, i: number) => <SelectOption
                key={i}
                value={ { id: test.id, owner: test.owner, toString: () => test.name } as SelectedTest}
         />) : [])
    ] }</Select>)
}