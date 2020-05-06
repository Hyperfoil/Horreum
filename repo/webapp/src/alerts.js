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
   }
   return state;
}

const alertsSelector = state => state.alerts

export default () => {
   const alerts = useSelector(alertsSelector)
   const dispatch = useDispatch()
   if (alerts.length == 0) {
      return ""
   }
   return (<div style={{ position: "absolute" }}>
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
