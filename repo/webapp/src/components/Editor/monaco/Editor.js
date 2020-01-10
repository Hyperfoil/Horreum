import React, {useRef} from 'react';
import { render } from 'react-dom';
// import MonacoEditor from 'react-monaco-editor';
// import 'monaco-editor/dev/vs/editor/editor.main.css'
// import 'monaco-editor/dev/vs/loader.js'
// import 'monaco-editor/dev/vs/language/json/jsonMode.js'
// import 'monaco-editor/dev/vs/language/json/jsonWorker.js'

import Editor, {monaco as getMonaco} from '@monaco-editor/react';

import beautify from 'js-beautify';

import {prepForEditor} from '../Editor';

let monaco;

getMonaco
  .init()
  .then(_monaco => {
    monaco = _monaco;
      /* here is the instance of monaco, so you can use the `monaco.languages` or whatever you want */
  })
  .catch(error => console.error('An error occurred during initialization of Monaco: ', error));

export default ({value = "{}", language="json", setValueGetter = (v) => {}, options = {} }) => {
    
    const valueGetter = useRef();

    const editorWillMount = (monaco)=>{
        console.log("editorWillMount",monaco)

        /*
                monaco.languages.json.jsonDefaults.setDiagnosticsOptions({
            validate: true,
            schemas: [{
                uri: "http://myserver/foo-schema.json",
                fileMatch: ['*'],
                schema: {
                    type: "object",
                    properties: {
                        p1: {
                            enum: [ "v1", "v2"]
                        },
                        p2: {
                            $ref: "http://myserver/bar-schema.json"
                        }
                    }
                }
            }]
        });

        */
    }

    const editorDidMount =(getter, editor) => {
        //console.log("editorDidMount",editor,monaco)
        console.log("editor",editor)
        valueGetter.current = getter;
        setValueGetter({ getValue:()=>valueGetter.current()});
        console.log("monaco.keys",Object.keys(monaco))
        editor.addAction({
            id: 'my-unique-id',
            label: 'my label',
            keybindings: [
                monaco.KeyMod.CtrlCmd | monaco.KeyCode.KEY_S
            ],
            precondition: null,
            keybindingContext: null,
            contextMenuGroupId: 'navigation',
            contextMenuOrder: 1.5,
            run: function(ed) {
                console.log("Ctrl+S => " + ed.getPosition());
                return null;
            }
        })



    }
    const _onChange = (newValue, e)=>{
        console.log('onChange', newValue, e);
      }

    return (
        // <MonacoEditor
        //     language="json"
        //     theme="vs-dark"
        //     defaultValue={"{}"}
        //     options={options}
        //     onChange={_onChange}
        //     editorDidMount={editorDidMount}
        //     editorWillMount={editorWillMount}
        // />
        <Editor
            value={value}
            language="json"
            theme="dark" //light | dark
            options={{
                //renderLineHighlight : 'none',
                ...options
            }}
            editorDidMount={editorDidMount}
            editorWillMount={editorWillMount}
        />
    )
}