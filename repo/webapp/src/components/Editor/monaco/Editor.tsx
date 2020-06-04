import React, {useRef} from 'react';

import Editor, {Monaco, monaco as getMonaco, EditorProps, EditorDidMount} from '@monaco-editor/react';
import { editor } from 'monaco-editor/esm/vs/editor/editor.api';

let monaco: Monaco;

getMonaco
  .init()
  .then(_monaco => {
    monaco = _monaco;
      /* here is the instance of monaco, so you can use the `monaco.languages` or whatever you want */
  })
  .catch(error => console.error('An error occurred during initialization of Monaco: ', error));

export type ValueGetter = {
    getValue(): string | undefined
}

export default ({value = "{}", language="json", setValueGetter = (_: ValueGetter) => {}, options = {} }) => {

    const valueGetter = useRef<() => string>();

    const editorDidMount: EditorDidMount = (getEditorValue: () => string, editor: editor.IStandaloneCodeEditor) => {
        valueGetter.current = getEditorValue;
        setValueGetter({ getValue: () => valueGetter.current ? valueGetter.current() : undefined });
        editor.addAction({
            id: 'my-unique-id',
            label: 'my label',
            keybindings: [
                monaco.KeyMod.CtrlCmd | monaco.KeyCode.KEY_S
            ],
            precondition: undefined,
            keybindingContext: undefined,
            contextMenuGroupId: 'navigation',
            contextMenuOrder: 1.5,
            run: ed => {
                console.log("Ctrl+S => " + ed.getPosition());
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