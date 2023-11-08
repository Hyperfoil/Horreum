import {useState, useEffect, useContext} from "react"
import { useSelector } from "react-redux"

import { Form, FormGroup, Switch, TextArea, TextInput } from "@patternfly/react-core"

import FolderSelect from "../../components/FolderSelect"
import OptionalFunction from "../../components/OptionalFunction"
import { TabFunctionsRef } from "../../components/SavedTabs"

import {Test, Access, sendTest} from "../../api"
import { useTester, defaultTeamSelector } from "../../auth"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";

type GeneralProps = {
    test?: Test
    onTestIdChange(id: number): void
    onModified(modified: boolean): void
    funcsRef: TabFunctionsRef
}

export default function General({ test, onTestIdChange, onModified, funcsRef }: GeneralProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const defaultRole = useSelector(defaultTeamSelector)
    const [name, setName] = useState("")
    const [folder, setFolder] = useState("")
    const [description, setDescription] = useState("")
    const [compareUrl, setCompareUrl] = useState<string | undefined>(undefined)
    const [notificationsEnabled, setNotificationsEnabled] = useState(true)

    const updateState = (test?: Test) => {
        setName(test?.name || "")
        setFolder(test?.folder || "")
        setDescription(test?.description || "")
        setCompareUrl(test?.compareUrl?.toString() || undefined)
        setNotificationsEnabled(!test || test.notificationsEnabled)
    }

    useEffect(() => {
        if (!test) {
            return
        }
        updateState(test)
    }, [test])

    funcsRef.current = {
        save: () => {
            const newTest: Test = {
                id: test?.id || 0,
                name,
                folder,
                description,
                compareUrl: compareUrl || undefined, // when empty set to undefined
                notificationsEnabled,
                fingerprintLabels: [],
                fingerprintFilter: undefined,
                owner: test?.owner || defaultRole || "__test_created_without_a_role__",
                access: test ? test.access : Access.Private,
                tokens: [],
                transformers: [],
            }
            return sendTest(newTest, alerting).then(response => onTestIdChange(response.id))
        },
        reset: () => updateState(test),
    }

    const isTester = useTester(test?.owner)
    return (
        <>
            <Form isHorizontal={true} >
                <FormGroup
                    label="Name"
                    isRequired={true}
                    fieldId="name"
                    helperText="Test names must be unique"
                    helperTextInvalid="Name must be unique and not empty"
                >
                    <TextInput
                        value={name || ""}
                        isRequired
                        type="text"
                        id="name"
                        aria-describedby="name-helper"
                        name="name"
                        isReadOnly={!isTester}
                        validated={name !== null && name.trim().length > 0 ? "default" : "error"}
                        onChange={n => {
                            setName(n)
                            onModified(true)
                        }}
                    />
                </FormGroup>
                <FormGroup label="Folder" fieldId="folder">
                    <FolderSelect folder={folder} onChange={setFolder} canCreate={true} readOnly={!isTester} />
                </FormGroup>
                <FormGroup label="Description" fieldId="description" helperText="" helperTextInvalid="">
                    <TextArea
                        value={description || ""}
                        type="text"
                        id="description"
                        aria-describedby="description-helper"
                        name="description"
                        readOnly={!isTester}
                        onChange={desc => {
                            setDescription(desc)
                            onModified(true)
                        }}
                    />
                </FormGroup>
                <FormGroup label="Notifications" fieldId="notifications">
                    <Switch
                        id="notifications-switch"
                        label="Notifications are enabled"
                        labelOff="All notifications are disabled"
                        isDisabled={!isTester}
                        isChecked={notificationsEnabled}
                        onChange={value => {
                            setNotificationsEnabled(value)
                            onModified(true)
                        }}
                    />
                </FormGroup>
                <FormGroup
                    label="Compare URL function"
                    fieldId="compareUrl"
                    helperText="This function receives an array of ids as first argument and auth token as second. It should return URL to comparator service."
                >
                    <OptionalFunction
                        readOnly={!isTester}
                        func={compareUrl}
                        defaultFunc="(ids, token) => 'http://example.com/compare?ids=' + ids.join(',')"
                        addText="Add compare function..."
                        undefinedText="Compare function is not defined"
                        onChange={value => {
                            setCompareUrl(value)
                            onModified(true)
                        }}
                    />
                </FormGroup>
            </Form>
        </>
    )
}
