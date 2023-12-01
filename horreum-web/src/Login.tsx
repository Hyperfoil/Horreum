import {useContext, useEffect, useState} from "react";
import {AppContext} from "./context/appContext";
import {AppContextType} from "./context/@types/appContextTypes";
import {Button, Form, FormGroup, Modal, Spinner, TextInput} from "@patternfly/react-core";
import {userApi} from "./api";
import store from "./store";
import {BASIC_AUTH, UPDATE_ROLES, AFTER_LOGOUT} from "./auth";

type LoginModalProps = {
    username: string
    password: string
    isOpen: boolean
    onClose(): void
    onLoginSuccess?(): void
    onLoginError?(): void
}

export default function LoginModal(props: LoginModalProps) {
    const {alerting} = useContext(AppContext) as AppContextType;
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
                    store.dispatch({type: BASIC_AUTH, username, password});

                    // TODO: instead of fetching userdata, should be fetching the user roles instead
                    userApi.info([username || ''])
                        .then(userdata => {
                                alerting.dispatchInfo("LOGIN", "Log in successful", "Successful log in of user " + userdata[0].username, 3000)
                                store.dispatch({type: UPDATE_ROLES, authenticated: true, roles: [/* TODO: the roles fetched */]});
                            },
                            error => {
                                alerting.dispatchInfo("LOGIN", "Failed to authenticate", error, 30000)
                                store.dispatch({type: AFTER_LOGOUT});
                                props.onLoginError?.()
                            })
                        .catch(error => alerting.dispatchInfo("LOGIN", "Could not perform authentication", error, 30000));

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
