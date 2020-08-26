import React, { useState, useRef } from 'react'

import { useDispatch} from 'react-redux'

import {
    Button,
    Modal,
    Progress,
} from '@patternfly/react-core'

import { recalculate, recalculateProgress } from './api'
import { alertAction } from '../../alerts'

type RecalculateModalProps = {
    title: string,
    recalculate: string,
    cancel: string,
    message: string,
    isOpen: boolean,
    onClose(): void,
    testId: number,
}

export default (props : RecalculateModalProps) => {
    const [progress, setProgress] = useState(-1)
    const dispatch = useDispatch()
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
            props.onClose()
        }}
        actions={ progress < 0 ? [
            <Button
                variant="primary"
                onClick={() => {
                    setProgress(0)
                    recalculate(props.testId).then(
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
                variant="secondary"
                onClick={ props.onClose }
            >{ props.cancel }</Button>
        ] : []}
    >
        { progress < 0 && props.message }
        { progress >= 0 && <Progress value={progress} title="Recalculating..." measureLocation="inside" /> }
    </Modal>)
}
