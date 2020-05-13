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

    const editorDidMount =(getter, editor) => {
        //console.log("editorDidMount",editor,monaco)
        valueGetter.current = getter;
        setValueGetter({ getValue:()=>valueGetter.current()});
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

    return (
        <Editor
            value={value}
            language="json"
            theme="dark" //light | dark
            options={{
                //renderLineHighlight : 'none',
                ...options
            }}
            editorDidMount={editorDidMount}
        />
    )
}