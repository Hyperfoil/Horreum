import { Alert as PatternflyAlert, AlertActionCloseButton, AlertVariant } from "@patternfly/react-core"

import {AppContext} from "./context/appContext";
import React, {useContext} from "react";
import {AppContextType} from "./context/@types/appContextTypes";

export const ADD_ALERT = "alert/ADD"
export const CLEAR_ALERT = "alert/CLEAR"

export interface Alert {
    title: string
    type: string
    variant?: AlertVariant
    content: Element | string | undefined
}

export interface ConstraintValidationException {
    error: string
    violations: ConstraintViolation[]
}

export interface ConstraintViolation {
    class: string
    path: string
    message: string
}


export const constraintValidationFormatter = (object: any) => (e: any) => {
    if (e && e.error && e.error === "jakarta.validation.ConstraintViolationException") {
        return (
            <>
                <span>Some constraints on {object} have failed:</span>
                <br />
                <ul>
                    {(e as ConstraintValidationException).violations.map((v, i) => (
                        <li key={i}>
                            <code>
                                {v.class}/{v.path}
                            </code>
                            : {v.message}
                        </li>
                    ))}
                </ul>
            </>
        )
    } else {
        return false
    }
}

export function defaultFormatError(e: any) {
    console.log(e)
    if (!e) {
        return ""
    }
    if (typeof e === "string") {
        try {
            e = JSON.parse(e)
        } catch {
            /* noop */
        }
    }
    if (typeof e !== "object") {
        return String(e)
    } else if (e instanceof Error) {
        return e.toString()
    } else {
        return <pre>{JSON.stringify(e, null, 2)}</pre>
    }
}

export const contextAlertAction = (
    type: string,
    title: string,
    e: any,
    ...errorFormatter: ((error: any) => any)[]
): Alert => {
    let formatted = undefined
    for (const f of errorFormatter) {
        formatted = f.call(null, e)
        if (formatted) break
    }
    if (!formatted) {
        formatted = defaultFormatError(e)
    }
    const newAlert: Alert = {
        type,
        title,
        content: formatted,
    }
    return newAlert;
}

function Alerts() {
    const { alerting } = useContext(AppContext) as AppContextType;
    if (alerting.alerts.length === 0) {
        return <></>
    }
    return (
        <div style={{ position: "absolute", zIndex: 1000, width: "100%" }}>
            {alerting.alerts.map((alert, i) => (
                <PatternflyAlert
                    key={i}
                    variant={alert.variant || "warning"}
                    title={alert.title || "Title is missing"}
                    actionClose={
                        <AlertActionCloseButton
                            onClose={() => { alerting.clearAlert (alert)}}
                        />
                    }
                >
                    {alert.content}
                </PatternflyAlert>
            ))}
        </div>
    )
}

export default Alerts
