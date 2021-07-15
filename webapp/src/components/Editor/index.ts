
function isString(value: any) {
    return typeof value === 'string' || value instanceof String;
}

function isNumber(value: any) {
    return typeof value === 'number' && isFinite(value);
}

function pad(amount: number) {
    if (amount <= 0) {
        return ""
    }
    return "".padStart(amount, " ");
}

export const toString = (obj: any, left = 0, step = 2): string => {
    if (obj == null) {
        return "null"
    } else if (isNumber(obj)) {
        return String(obj)
    } else if (isString(obj)) {
        //could be a function
        if (obj.includes("=>") || obj.startsWith("function")){
            const from = fromEditor(obj);
            return from.toString()
        } else {
            return ("\"" + obj.replace(/"/g,"\\\"") + "\"").replace(/\n/g, "\\n");
        }
    } else if (Array.isArray(obj)) {
        return "[\n" + obj.filter(e=>typeof e !== "undefined").map((e,i) => pad(left + step) + toString(e, left + step, step) + (i<obj.length-1?",":"")+"\n").reduce((a,b)=>a+b,'') + "]"
    } else if (typeof obj === "function") {
        return obj.toString();
    } else if (typeof obj === "object") {
        let loop = "{\n"
        Object.keys(obj).forEach((k,i,ary) => {
            const v = obj[k]
            loop = loop + pad(left + step) +"\"" +k + "\" : " + toString(v, left + step, step) +(i<ary.length-1?",":"") +"\n"
        })
        return loop + pad(left) + "}";
    } else { // booleans?
        return String(obj);
    }
}

export const fromEditor = (str?: string) => {
    try {
        if (isString(str)) {
            // eslint-disable-next-line
            const factory = new Function("return " + str)
            const newValue = factory()
            return newValue
        } else {
            return str;
        }
    } catch (e) {
        console.error(e);
        return false
    }
};