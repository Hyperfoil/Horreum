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
    isOpen: boolean,
    onClose(): void,
    testId: number,
}

export default ({ isOpen, onClose, testId } : RecalculateModalProps) => {
    const [progress, setProgress] = useState(-1)
    const dispatch = useDispatch()
    const timer = useRef<number>()
    const fetchProgress = () => {
        recalculateProgress(testId).then(
            response => {
                if (response.done) {
                    setProgress(-1)
                    window.clearInterval(timer.current)
                    onClose()
                } else {
                    setProgress(response.percentage)
                }
            },
            error => {
                setProgress(-1)
                window.clearInterval(timer.current)
                onClose()
                dispatch(alertAction("RECALC_PROGRESS", "Cannot query recalculation progress", error))
            }
        )
    }
    return (<Modal
        variant="small"
        title="Confirm recalculation"
        isOpen={isOpen}
        onClose={() => {
            setProgress(-1)
            onClose()
        }}
        actions={ progress < 0 ? [
            <Button
                variant="primary"
                onClick={() => {
                    setProgress(0)
                    recalculate(testId).then(
                        _ => {
                            timer.current = window.setInterval(fetchProgress, 1000)
                        },
                        error => {
                            setProgress(-1)
                            onClose()
                            dispatch(alertAction("RECALCULATION", "Failed to start recalculation", error))
                        }
                    )
                }}
            >Confirm recalculation</Button>,
            <Button
                variant="secondary"
                onClick={ onClose }
            >Cancel</Button>
        ] : []}
    >
        { progress < 0 && "Really drop all datapoints, calculating new ones?" }
        { progress >= 0 && <Progress value={progress} title="Recalculating..." measureLocation="inside" /> }
    </Modal>)
}
