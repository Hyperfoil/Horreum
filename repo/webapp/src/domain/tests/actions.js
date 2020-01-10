import * as api from './api';
import * as actionTypes from './actionTypes';

const loaded = tests =>({
    type: actionTypes.LOADED,
    tests: Array.isArray(tests) ? tests: [tests]
})
export const fetchSummary = () => {
    return dispatch => {
        api.summary()
        .then(response=>{
            dispatch(loaded(response))
        })
    }
};
export const fetchTest = (id) => {
    return dispatch =>
        api.get(id)
        .then(response=>{
            dispatch(loaded(response))
        })
}
export const sendTest = (test) => {
    return dispatch =>
        api.send(test)
        .then(response=>{
            console.log("sendTest.response",response)
            dispatch(loaded(response));
        })
}