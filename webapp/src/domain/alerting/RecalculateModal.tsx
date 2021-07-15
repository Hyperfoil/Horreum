import React, { useState, useRef } from 'react'

import { useDispatch} from 'react-redux'

import {
    Button,
    Checkbox,
    Form,
    FormGroup,
    Modal,
    Progress,
} from '@patternfly/react-core'

import { recalculate, recalculateProgress } from './api'
import { alertAction } from '../../alerts'
import TimeRangeSelect, { TimeRange } from '../../components/TimeRangeSelect'

type RecalculateModalProps = {
    title: string,
    recalculate: string,
    cancel: string,
    message: string,
    isOpen: boolean,
    onClose(): void,
    testId: number,
}

export default function RecalculateModal(props : RecalculateModalProps) {
    const [progress, setProgress] = useState(-1)
    const dispatch = useDispatch()
    const [debug, setDebug] = useState(false)
    const [timeRange, setTimeRange] = useState<TimeRange>()
    const timer = useRef<number>()
    const fetchProgress = () => {
        recalculateProgress(props.testId).then(
            response => {
                if (response.done) {
                    setProgress(-1)
                    window.clearInterval(timer.current)
                    props.onClose()
                } else {
                    setProgress(response.percentage)
                }
            },
            error => {
                setProgress(-1)
                window.clearInterval(timer.current)
                props.onClose()
                dispatch(alertAction("RECALC_PROGRESS", "Cannot query recalculation progress", error))
            }
        )
    }
    return (<Modal
        variant="small"
        title={props.title}
        isOpen={props.isOpen}
        onClose={() => {
            setProgress(-1)
            if (timer.current) {
                window.clearInterval(timer.current)
            }
            props.onClose()
        }}
        actions={ progress < 0 ? [
            <Button
                key={1}
                variant="primary"
                onClick={() => {
                    setProgress(0)
                    recalculate(props.testId, debug, timeRange?.from, timeRange?.to).then(
                        _ => {
                            timer.current = window.setInterval(fetchProgress, 1000)
                        },
                        error => {
                            setProgress(-1)
                            props.onClose()
                            dispatch(alertAction("RECALCULATION", "Failed to start recalculation", error))
                        }
                    )
                }}
            >{ props.recalculate }</Button>,
            <Button
                key={2}
                variant="secondary"
                onClick={ props.onClose }
            >{ props.cancel }</Button>
        ] : []}
    >
        { progress < 0 && <Form isHorizontal>
            { props.message }
            <FormGroup label="Runs from:" fieldId="timeRange">
                <TimeRangeSelect
                    selection={ timeRange }
                    onSelect={ setTimeRange } />
            </FormGroup>
            <FormGroup label="Debug logs:" fieldId="debug">
                <Checkbox
                    id="debug"
                    isChecked={debug}
                    onChange={setDebug}
                    label="Write debug logs" />
            </FormGroup>
        </Form> }
        { progress >= 0 && <Progress value={progress} title="Recalculating..." measureLocation="inside" /> }
    </Modal>)
}
