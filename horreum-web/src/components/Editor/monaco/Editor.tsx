import { useRef } from "react"

import MonacoEditor, { useMonaco, OnMount } from "@monaco-editor/react"
import { editor } from "monaco-editor/esm/vs/editor/editor.api"

type EditorProps = {
    value?: string
    language?: string
    options: any
    onChange?(value: string | undefined): void
    height?: number | string
}

export default function Editor(props: EditorProps) {
    const monaco = useMonaco()
    const valueGetter = useRef<() => string>(undefined)

    // requires re-render. does not sync with localStorage
    // the storage event is not triggered on the window where the change is made
    const dark = localStorage.getItem('dark-theme') === 'true';

    const onMount: OnMount = (editor: editor.IStandaloneCodeEditor) => {
        valueGetter.current = () => editor.getValue()
        if (!monaco) {
            return
        }
        editor.addAction({
            id: "my-unique-id",
            label: "my label",
            keybindings: [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS],
            precondition: undefined,
            keybindingContext: undefined,
            contextMenuGroupId: "navigation",
            contextMenuOrder: 1.5,
            run: ed => {
                console.log("Ctrl+S => " + ed.getPosition())
            },
        })
    }

    return (
        <MonacoEditor
            value={props.value}
            language={props.language || "json"}
            theme={dark ? 'vs-dark' : 'light'}
            options={{
                //renderLineHighlight : 'none',
                ...props.options,
                language: props.language || "json",
                automaticLayout: true,
                scrollBeyondLastLine: false,
                stickyScroll: {
                    defaultModel: 'indentationModel',
                    enabled: true,
                    maxLineCount: 10,
                    scrollWithEditor: true,
                }
            }}
            height={props.height}
            onMount={onMount}
            onChange={props.onChange}
        />
    )
}
