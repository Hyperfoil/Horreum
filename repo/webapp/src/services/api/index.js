import fetchival from 'fetchival';
import apiConfig from './config';
import store from "../../store.js"
import { REQUEST_FAILED } from "../../auth.js"

export const exceptionExtractError = (exception) => {
	if (!exception.Errors) return false;
	let error = false;
	const errorKeys = Object.keys(exception.Errors);
	if (errorKeys.length > 0) {
		error = exception.Errors[errorKeys[0]][0].message;
	}
	return error;
};
const serialize = (input)=>{
	if(input === null || input === undefined){
		return input
	}else if(Array.isArray(input)){
        return input.map(v=>serialize(v))
    }else if (typeof input === "function"){
        return input.toString()
    }else if (typeof input === "object"){
        const rtrn = {}
        Object.keys(input).forEach(key=>{
            rtrn[key] = serialize(input[key])
        })
        return rtrn;
    }else{
        return input;
    }
}
const deserialize = (input)=>{
	if(input === null || input === undefined){
		return input
	}else if(Array.isArray(input)){
        return input.map(v=>deserialize(v))
    }else if (typeof input === "object"){
        const rtrn = {}
        Object.keys(input).forEach(key=>{
            rtrn[key] = deserialize(input[key])
        })
        return rtrn;
    }else if (typeof input === "string"){
        if(input.includes("=>") || input.startsWith("function ")){
            return new Function("return "+input)();
        }else{
            return input;
        }
    }else{
        return input;
    }
}

export const fetchApi = (endPoint, payload = {}, method = 'get', headers = {}, responseAs = 'json') => {
	//const accessToken = sessionSelectors.get().tokens.access.value;
	const serialized = serialize(payload)
	const keycloak = store.getState().auth.keycloak
   let updateTokenPromise;
	if (keycloak != null && keycloak.authenticated) {
	   updateTokenPromise = keycloak.updateToken(30)
	} else {
	   updateTokenPromise = Promise.resolve(false)
   }
	return updateTokenPromise.then(() => {
	   let authHeaders = {}
      if (keycloak != null && keycloak.token != null) {
         authHeaders.Authorization = "Bearer " + keycloak.token;
      }
      return fetchival(`${apiConfig.url}${endPoint}`, {
         headers: { ...authHeaders, ...headers },
         responseAs: responseAs,
      })[method.toLowerCase()](serialized)
   }).then(response => {
      return Promise.resolve(deserialize(response));
   }, (e) => {
      if (e.response) {
         if (e.response.status === 401 || e.response.status === 403) {
            store.dispatch({ type: REQUEST_FAILED })
         }
         return e.response.json().then(body => {
            return Promise.reject(body)
         }, (noBody) => {
            return e.response.text().then(body => {
               return Promise.reject(body);
            }, (noBodyAgain) => {
               return Promise.reject(e);
            })
         })
      } else {
         return Promise.reject(e);
      }
   });
};