import React from 'react';

import { useDispatch, useSelector } from 'react-redux'

import {
  Alert,
  AlertActionCloseButton,
} from '@patternfly/react-core';

export const ADD_ALERT = "alert/ADD"
export const CLEAR_ALERT = "alert/CLEAR"

export const reducer = (state = [], action) => {
   switch (action.type) {
      case ADD_ALERT:
         return [...state, action.alert]
      case CLEAR_ALERT:
         if (action.alert) {
            return state.filter(a => a.type !== action.alert.type && a.title !== action.alert.title)
         } else {
            return []
         }
         break;
      default:
   }
   return state;
}

export const constraintValidationFormatter = object => e => {
    if (e && e.error === "javax.validation.ConstraintViolationException") {
       return (<><span>Some constraints on { object } have failed:</span><br /><ul>
          { e.violations.map((v, i) => (<li key={i}><code>{v.class}/{v.path}</code>: {v.message}</li>))}
       </ul></>)
    } else {
       return false
    }
 }

export const alertAction = (type, title, e, errorFormatter = []) => {
   let formatted = false;
   if (Array.isArray(errorFormatter)) {
      for (const f of errorFormatter) {
         formatted = f.call(null, e);
         if (formatted) break;
      }
   } else {
      formatted = errorFormatter.call(null, e)
   }
   if (!formatted) {
      formatted = defaultFormatError(e)
   }
   return {
      type: ADD_ALERT,
      alert: {
         type, title, content: formatted
      }
   }
}

export const defaultFormatError = e => {
   if (!e) {
      return ""
   } else if (typeof e !== "object") {
      return String(e)
   } else if (e instanceof Error) {
      return e.toString()
   } else {
      return JSON.stringify(e)
   }
}

const alertsSelector = state => state.alerts

export default () => {
   const alerts = useSelector(alertsSelector)
   const dispatch = useDispatch()
   if (alerts.length === 0) {
      return ""
   }
   return (<div style={{ position: "absolute", zIndex: "1000", width: "100%" }}>
      { alerts.map(alert => (
         <Alert variant={ alert.variant || "warning" }
                title={ alert.title || "Title is missing" }
                action={<AlertActionCloseButton onClose={() => {
                    dispatch({ type: CLEAR_ALERT, alert: { type: alert.type }})
                }} />} >
            { alert.content }
         </Alert>
      ))}
      </div>)
}
