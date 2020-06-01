import * as api from './api';
import * as actionTypes from './actionTypes';
import { accessName } from '../../auth'

const loaded = schema => ({
   type: actionTypes.LOADED,
   schemas: Array.isArray(schema) ? schema : [schema]
})

export const getById = id => dispatch =>
    api.getById(id).then(
       response => dispatch(loaded(response)),
       error => dispatch(loaded([]))
    )

// TODO: unused, remove me?
export const getByName = name => dispatch =>
    api.getByName(name).then(
       response => dispatch(loaded(response)),
       error => dispatch(loaded([]))
    )

export const add = payload => dispatch =>
   api.add(payload)

export const all = () => dispatch =>
   api.all().then(
      response => dispatch(loaded(response)),
      error => dispatch(loaded([]))
   )

export const resetToken = id => dispatch =>
   api.resetToken(id).then(
       token => dispatch({
            type: actionTypes.UPDATE_TOKEN,
            id: id,
            token: token,
       })
   )

export const dropToken = (id) => dispatch =>
   api.dropToken(id).then(
      () => dispatch({
            type: actionTypes.UPDATE_TOKEN,
            id: id,
            token: null,
      })
   )

export const updateAccess = (id, owner, access) => dispatch =>
   api.updateAccess(id, owner, accessName(access)).then(
      () => dispatch({ type: actionTypes.UPDATE_ACCESS, id, owner, access })
   )

export const deleteSchema = id => dispatch => api.deleteSchema(id).then(() => {
   dispatch({
      type: actionTypes.DELETE,
      id: id,
   })
})
