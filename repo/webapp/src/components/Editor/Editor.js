import React, {useMemo} /*, { useState, useEffect }*/ from 'react';
//material-ui
import { makeStyles } from '@material-ui/core/styles';

//CodeMirror
//import CodeMirror from 'codemirror';
import { Controlled as ReactCodeMirror } from 'react-codemirror2'
import 'codemirror/lib/codemirror.css';
import 'codemirror/theme/neat.css';
import 'codemirror/mode/javascript/javascript';
//import 'codemirror/mode/yaml/yaml';
//linting

import 'codemirror/addon/lint/lint';
import 'codemirror/addon/lint/lint.css'
//import 'codemirror/addon/lint/javascript-lint';
import { JSHINT } from 'jshint';//for linting
//import jsonlint from 'codemirror/addon/lint/json-lint';
import 'codemirror/addon/lint/json-lint';
import './my-javascript-lint';

//import 'codemirror/addon/lint/yaml-lint';
//import jsyaml from 'js-yaml';
//folding
import 'codemirror/addon/fold/foldcode';
import 'codemirror/addon/fold/brace-fold';
import 'codemirror/addon/fold/foldgutter';
import 'codemirror/addon/fold/foldgutter.css';
import 'codemirror/addon/edit/matchbrackets';
import 'codemirror/addon/edit/closebrackets';
//hinting
import 'codemirror/addon/hint/show-hint'
import 'codemirror/addon/hint/show-hint.css'
import './my-javascript-hint';


import beautify from 'js-beautify';

//for codemirror lint @see https://github.com/JedWatson/react-codemirror/issues/89
window.JSHINT = JSHINT
window.jsonlint = JSHINT //for json-lint
//window.jsyaml = jsyaml

function isString(value) {
    return typeof value === 'string' || value instanceof String;
}
function isNumber(value) {
    return typeof value === 'number' && isFinite(value);
}
function pad(amount) {
    //return "";
    if (amount <= 0) {
        return ""
    }
    return "".padStart(amount, " ");
}
export const toString = (obj, left = 0, step = 2) => {
    let rtrn = false;
    if (isNumber(obj)) {
        rtrn = obj
    } else if (isString(obj)) {
        //could be a function
        if(obj.includes("=>") || obj.startsWith("function")){
            const from = fromEditor(obj);
            rtrn = from.toString()
        }else{
            rtrn =("\"" + obj.replace(/"/g,"\\\"") + "\"").replace(/\n/g, "\\n");
        }
    } else if (Array.isArray(obj)) {
        rtrn = "[\n" + obj.filter(e=>typeof e !== "undefined").map((e,i) => pad(left + step) + toString(e, left + step, step) + (i<obj.length-1?",":"")+"\n").reduce((a,b)=>a+b,'') + "]"
    } else if (typeof obj === "function") {
        rtrn = obj.toString();
    } else if (typeof obj === "object") {
        let loop = "{\n"
        Object.keys(obj).forEach((k,i,ary) => {
            const v = obj[k]
            loop = loop + pad(left + step) +"\"" +k + "\" : " + toString(v, left + step, step) +(i<ary.length-1?",":"") +"\n"
        })
        rtrn = loop + pad(left) + "}";
    }else{ // booleans?
        rtrn = obj;
    }
    return rtrn;
}
const useStyles = makeStyles(theme => ({
    editor: {
        flexGrow: 1,
        position: 'relative',
        height: '100%',
        '&> .CodeMirror': {
            position: 'absolute',
            width: '100%',
            height: '100%',
        }
    },
}));
export const fromEditor = (str)=>{
    try {
        if(isString(str)){
            const factory = new Function("return " + str)
            const newValue = factory()
            return newValue
        }else{
            return str;
        }
    } catch (e) {
        console.log("WTF happened in fromEditor",e);
        return false
    }
};
export const prepForEditor = (obj)=>{
    const data = toString(obj)
    const nice = beautify(data, { indent_size: 2, space_in_empty_paren: true, brace_style: 'expand' })
    return nice;
};
export default ({ value = "{}", onChange = (v) => { }, options = {} }) => {
    const classes = useStyles();
    const renderValue = useMemo(()=>{
        if(value instanceof Object){
            const postPrep = prepForEditor(value);
            return postPrep;
        }else{
            return value;
        }
        
    },[value])
    return (
        <ReactCodeMirror
            className={classes.editor}
            value={renderValue}
            options={{                
                mode: 'javascript',
                theme: 'neat',
                lineNumbers: true,
                tabSize: 2,
                indentUnit: 0,
                autofocus: true,
                extraKeys: {
                    "Ctrl-Q": function (cm) { cm.foldCode(cm.getCursor()); },
                    "Ctrl-S": function (cm) { console.log("ha!") },
                    "Alt-Up": function (cm) { console.log("Alt-Up") },
                    "Ctrl-Space": "autocomplete",
                },
                gutters: ["CodeMirror-lint-markers", "CodeMirror-foldgutter"],
                lint: {
                    options: {
                        esversion: 8,
                    }
                },//requires lint.js
                // hintOptions: {
                //     hint: (cm,option)=>{
                //         var cursor = cm.getCursor(), line = cm.getLine(cursor.line)
                //         var start = cursor.ch, end = cursor.ch
                //         while (start && /\w/.test(line.charAt(start - 1))) --start
                //         while (end < line.length && /\w/.test(line.charAt(end))) ++end
                //         var word = line.slice(start, end).toLowerCase()                
                //         return new Promise(function(accept){
                //             accept({
                //                 list:["one","two"],
                //                 from: CodeMirror.Pos(cursor.line,start),
                //                 to: CodeMirror.Pos(cursor.line,end)
                //             })
                //         })
                //     }
                // },
                foldGutter: true,
                matchBrackets: true,
                autoCloseBrackets: true,
        
                ...options,
            }}
            onBeforeChange={(editor, data, value) => {
                onChange(value);
            }}
            // onChange={(editor, data, value) => {
                
            // }}
        />
    )
}