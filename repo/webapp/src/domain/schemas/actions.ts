import * as api from './api';
import * as actionTypes from './actionTypes';
import { accessName, Access } from '../../auth'
import { Schema, DeleteAction, LoadedAction, UpdateTokenAction, UpdateAccessAction } from './reducers';
import { Dispatch } from 'react';

const loaded = (schema: Schema | Schema[]): LoadedAction => ({
   type: actionTypes.LOADED,
   schemas: Array.isArray(schema) ? schema : [schema]
})

export const getById = (id: number) => (dispatch: Dispatch<LoadedAction>) =>
    api.getById(id).then(
       response => dispatch(loaded(response)),
       error => dispatch(loaded([]))
    )

// TODO: unused, remove me?
export const getByName = (name: string) => (dispatch: Dispatch<LoadedAction>) =>
    api.getByName(name).then(
       response => dispatch(loaded(response)),
       error => dispatch(loaded([]))
    )

export const add = (payload: Schema) => (dispatch: Dispatch<undefined>) =>
   api.add(payload)

export const all = () => (dispatch: Dispatch<LoadedAction>) =>
   api.all().then(
      response => dispatch(loaded(response)),
      error => dispatch(loaded([]))
   )

export const resetToken = (id: number) => (dispatch: Dispatch<UpdateTokenAction>) =>
   api.resetToken(id).then(
       token => dispatch({
            type: actionTypes.UPDATE_TOKEN,
            id: id,
            token: token,
       })
   )

export const dropToken = (id: number) => (dispatch: Dispatch<UpdateTokenAction>) =>
   api.dropToken(id).then(
      () => dispatch({
            type: actionTypes.UPDATE_TOKEN,
            id: id,
            token: null,
      })
   )

export const updateAccess = (id: number, owner: string, access: Access) => (dispatch: Dispatch<UpdateAccessAction>) =>
   api.updateAccess(id, owner, accessName(access)).then(
      () => dispatch({ type: actionTypes.UPDATE_ACCESS, id, owner, access })
   )

export const deleteSchema = (id: number) => (dispatch: Dispatch<DeleteAction>) =>
   api.deleteSchema(id).then(() => {
      dispatch({
         type: actionTypes.DELETE,
         id: id,
      })
   })
