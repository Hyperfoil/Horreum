import fetchival from 'fetchival';
import apiConfig from './config';

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

export const fetchApi = (endPoint, payload = {}, method = 'get', headers = {}) => {
	//const accessToken = sessionSelectors.get().tokens.access.value;
	const serialized = serialize(payload)
	return fetchival(`${apiConfig.url}${endPoint}`, {
		// headers: _.pickBy({
		// 	...(accessToken ? {
		// 		Authorization: `Bearer ${accessToken}`,
		// 	} : {
		// 		'Client-ID': apiConfig.clientId,
		// 	}),
		// 	...headers,
		// }, item => !_.isEmpty(item)),
	})[method.toLowerCase()](serialized)
	.then(response=>{
		return Promise.resolve(deserialize(response));
	},(e) => {
		if ( e.response){
			return e.response.json().then(body=>{
				return Promise.reject(body)
			},(noBody)=>{
				e.response.text().then(body=>{
					return Promise.reject(body);
				},(noBodyAgain)=>{
					return Promise.reject(e);
				})
			})
		
		}
		return Promise.reject(e);
		// if (e.response && e.response.json) {
		// 	e.response.json().then((json) => {
		// 		if (json) throw json;
		// 		throw e;
		// 	});
		// } else {
		// 	throw e;
		// }
	});
};