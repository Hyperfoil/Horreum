import {useContext, useEffect, useState} from "react";
import {AppContext} from "./context/AppContext";
import {Button, Form, FormGroup, Spinner, TextInput} from '@patternfly/react-core';
import {Modal} from '@patternfly/react-core/deprecated';
import {AuthBridgeContext} from "./context/AuthBridgeContext";
import {AuthContextType} from "./context/@types/authContextTypes";

type LoginModalProps = {
    username: string
    password: string
    isOpen: boolean
    onClose(): void
    onLoginSuccess?(): void
    onLoginError?(): void
}

export default function LoginModal(props: LoginModalProps) {
    const { signIn } = useContext(AuthBridgeContext) as AuthContextType;
    const [username, setUsername] = useState<string>()
    const [password, setPassword] = useState<string>()
    const [creating, setCreating] = useState(false)
    const valid = username && password
    useEffect(() => {
        setUsername(undefined)
        setPassword(undefined)
    }, [props.isOpen])

    return <Modal
        title="Horreum Login"
        isOpen={props.isOpen}
        onClose={props.onClose}
        variant={"small"}
        actions={[
            <Button
                isDisabled={!valid}
                variant={"primary"}
                key={"horreum-login-button"}
                onClick={() => {
                    setCreating(true)
                    void signIn(username, password)
                    setCreating(false);
                    props.onClose();
                    props.onLoginSuccess?.()
                }}
            >
                Login
            </Button>,
            <Button variant="secondary" key={"horreum-login-cancel-button"} onClick={props.onClose}>
                Cancel
            </Button>,
        ]}
    >
        {creating ? <Spinner size="xl"/> : <Form isHorizontal>
            <FormGroup isRequired label="Username" fieldId="username">
                <TextInput
                    aria-label={"horreum-login-username"}
                    isRequired
                    value={username || ""}
                    autoComplete={"username"}
                    onChange={(_,v)=>setUsername(v)}
                    validated={username ? "default" : "error"}
                />
            </FormGroup>
            <FormGroup isRequired label="Password" fieldId="password">
                <TextInput
                    aria-label={"horreum-login-password"}
                    isRequired
                    type={"password"}
                    value={password || ""}
                    autoComplete={"current-password"}
                    onChange={(_,v)=>setPassword(v)}
                    validated={password ? "default" : "error"}
                />
            </FormGroup>
        </Form>}
    </Modal>
}
