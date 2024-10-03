import {useState, useEffect, useContext} from "react"
import {
    Button,
    Checkbox,
    Form,
    FormGroup,
    HelperText,
    HelperTextItem,
    FormHelperText,
    List,
    ListItem,
    Modal,
    Spinner,
    TextInput,
} from "@patternfly/react-core"
import {userApi, UserData} from "../../api"
import { getRoles } from "./TeamMembers"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";


type NewUserModalProps = {
    team: string
    isOpen: boolean
    onClose(): void
    onCreate(user: UserData, roles: string[]): void
}

export default function NewUserModal(props: NewUserModalProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [username, setUsername] = useState<string>()
    const [password, setPassword] = useState<string>()
    const [email, setEmail] = useState<string>()
    const [firstName, setFirstName] = useState<string>()
    const [lastName, setLastName] = useState<string>()
    const [creating, setCreating] = useState(false)
    const [viewer, setViewer] = useState(true)
    const [tester, setTester] = useState(true)
    const [uploader, setUploader] = useState(false)
    const [manager, setManager] = useState(false)
    const valid = username && password && email && /^.+@.+\..+$/.test(email)
    useEffect(() => {
        setUsername(undefined)
        setPassword("")
        setEmail("")
        setFirstName("")
        setLastName("")
        setViewer(true)
        setTester(true)
        setUploader(false)
        setManager(false)
    }, [props.isOpen])
    return (
        <Modal
            title="Create new user"
            isOpen={props.isOpen}
            onClose={props.onClose}
            actions={[
                <Button
                    isDisabled={!valid}
                    onClick={() => {
                        setCreating(true)
                        const user = { id: "", username: username || "", email, firstName, lastName }
                        const roles = getRoles(viewer, tester, uploader, manager)
                        userApi.createUser({ user, password, team: props.team, roles })
                            .then(() => {
                                props.onCreate(user, roles)
                                alerting.dispatchInfo(
                                    "USER_CREATED",
                                    "User created",
                                    "User was successfully created",
                                    3000
                                )
                            })
                            .catch(error =>
                                alerting.dispatchError(error, "USER_NOT_CREATED", "Failed to create new user.")
                            )
                            .finally(() => {
                                setCreating(false)
                                props.onClose()
                            })
                    }}
                >
                    Create
                </Button>,
                <Button variant="secondary" onClick={props.onClose}>
                    Cancel
                </Button>,
            ]}
        >
            {creating ? (
                <Spinner size="xl" />
            ) : (
                <Form isHorizontal>
                    <FormGroup isRequired label="Username" fieldId="username">
                        <TextInput
                            isRequired
                            value={username}
                            onChange={(_event, val) => setUsername(val)}
                            validated={username ? "default" : "error"}
                        />
                    </FormGroup>
                    <FormGroup
                        isRequired
                        label="Temporary password"
                        fieldId="password"
                    >
                        <TextInput
                            isRequired
                            value={password}
                            onChange={(_event, val) => setPassword(val)}
                            validated={password ? "default" : "error"}
                        />
                        <FormHelperText>
                            <HelperText>
                                <HelperTextItem>This password is only temporary and the user will change it during first login.</HelperTextItem>
                            </HelperText>
                        </FormHelperText>                        
                    </FormGroup>
                    <FormGroup isRequired label="Email" fieldId="email">
                        <TextInput
                            isRequired
                            type="email"
                            value={email}
                            onChange={(_event, val) => setEmail(val)}
                            validated={email && /^.+@.+\..+$/.test(email) ? "default" : "error"}
                        />
                    </FormGroup>
                    <FormGroup label="First name" fieldId="firstName">
                        <TextInput value={firstName} onChange={(_event, val) => setFirstName(val)} />
                    </FormGroup>
                    <FormGroup label="Last name" fieldId="lastName">
                        <TextInput value={lastName} onChange={(_event, val) => setLastName(val)} />
                    </FormGroup>
                    <FormGroup label="Permissions" fieldId="permissions">
                        <List isPlain>
                            <ListItem>
                                <Checkbox id="viewer" isChecked={viewer} onChange={(_event, val) => setViewer(val)} label="Viewer" />
                            </ListItem>
                            <ListItem>
                                <Checkbox id="tester" isChecked={tester} onChange={(_event, val) => setTester(val)} label="Tester" />
                            </ListItem>
                            <ListItem>
                                <Checkbox id="uploader" isChecked={uploader} onChange={(_event, val) => setUploader(val)} label="Uploader" />
                            </ListItem>
                            <ListItem>
                                <Checkbox id="manager" isChecked={manager} onChange={(_event, val) => setManager(val)} label="Manager" />
                            </ListItem>
                        </List>
                    </FormGroup>
                </Form>
            )}
        </Modal>
    )
}
