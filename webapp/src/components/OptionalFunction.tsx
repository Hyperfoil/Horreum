import {
    Button
} from '@patternfly/react-core'
import Editor from './Editor/monaco/Editor'

type OptionalFunctionProps = {
    func: string | undefined,
    onChange(value: string): void,
    readOnly: boolean,
    undefinedText: string,
    addText: string,
    defaultFunc: string,
}

export default function OptionalFunction(props: OptionalFunctionProps) {
    if (props.func === undefined) {
        if (props.readOnly) {
            return <>{ props.undefinedText } </>
        } else {
            return (
                <Button
                    style={{ padding: 0, marginTop: "16px" }}
                    variant="link"
                    onClick={() => {
                        props.onChange(props.defaultFunc)
                    }}
                >{ props.addText }</Button>)
        }
    } else {
        return (
            <div style={{minHeight: "100px", height: "100px", resize: "vertical", overflow: "auto"}}>
                <Editor value={props.func}
                        onChange={ value => {
                            props.onChange(value || "")
                        }}
                        language="typescript"
                        options={{
                            wordWrap: 'on',
                            wrappingIndent: 'DeepIndent',
                            readOnly: props.readOnly
                        }}/>
            </div>
        )
    }
}