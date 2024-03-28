import React, {useState, useEffect, useContext } from "react"
import { useSelector } from "react-redux"

import {
    Form,
    FormGroup,
    HelperText,
    HelperTextItem,
    FormHelperText,
    FormSelect,
    FormSelectOption,
    Switch,
    TextArea,
    TextInput, FlexItem
} from "@patternfly/react-core"
import FolderSelect from "../../components/FolderSelect"
import OptionalFunction from "../../components/OptionalFunction"
import { TabFunctionsRef } from "../../components/SavedTabs"

import {Test, Access, sendTest, configApi, Datastore, apiCall} from "../../api"
import { useTester, defaultTeamSelector } from "../../auth"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import TestImportButton from "./TestImportButton";

type GeneralProps = {
    test?: Test
    onTestIdChange(id: number): void
    onModified(modified: boolean): void
    funcsRef: TabFunctionsRef
}

const isUrlOrBlank = (input:string|undefined):boolean=>{
    if(!input || input.trim().length == 0){
        return true;
    }
    if(!input.startsWith("http")){
        input=`http://${input}`
    }
    try{
        new URL(input)
    }catch(e){
        return false;
    }
    return true;
}

export default function TestSettings({ test, onTestIdChange, onModified, funcsRef }: GeneralProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const defaultRole = useSelector(defaultTeamSelector)
    const [name, setName] = useState("")
    const [folder, setFolder] = useState("")
    const [datastoreId, setDatastoreId] = useState(test?.datastoreId)
    const [description, setDescription] = useState("")
    const [compareUrl, setCompareUrl] = useState<string | undefined>(undefined)
    const [notificationsEnabled, setNotificationsEnabled] = useState(true)

    const [datastores, setDatastores] = useState<Datastore[]>([])
    const [owner] = useState(test?.owner || defaultRole || "")

    useEffect( () => {
        apiCall(configApi.datastores(owner), alerting, "DATASTORE", "Error occurred fetching datastores")
            .then(ds => setDatastores(ds))
    }, [test])

    const updateState = (test?: Test) => {
        setName(test?.name || "")
        setFolder(test?.folder || "")
        setDatastoreId( test?.datastoreId || 1 )
        setDescription(test?.description || "")
        setCompareUrl(test?.compareUrl?.toString() || undefined)
        setNotificationsEnabled(!test || test.notificationsEnabled)
    }
    const handleOptionChange = (_ : React.FormEvent<HTMLSelectElement>, value: string) => {
        setDatastoreId(parseInt(value))
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
                datastoreId: datastoreId || 1,
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

    const loadTests = () => {
        alerting.dispatchError("Not implemented", "Test import is not implemented yet", "IMPORT")
    }

    const isTester = useTester(test?.owner)
    const isUrlValid = isUrlOrBlank(compareUrl)
    return (
        <>
            <Form isHorizontal={true}>
                <FormGroup
                    label="Import"
                    isRequired={false}
                    fieldId="import"
                >
                    {/*TODO implement the import action for real*/}
                    <TestImportButton
                        // tests={allTests || []}
                        tests={[]}
                        onImported={() => loadTests()}
                    />

                </FormGroup>
                <FormGroup
                    label="Name"
                    isRequired={true}
                    fieldId="name"
                >
                    <TextInput
                        value={name || ""}
                        isRequired
                        type="text"
                        id="name"
                        aria-describedby="name-helper"
                        name="name"
                        //isReadOnly={!isTester} this is no longe rsupport
                        readOnlyVariant={isTester ? undefined : "default"}
                        validated={name !== null && name.trim().length > 0 ? "default" : "error"}
                        onChange={(_event, n) => {
                            setName(n)
                            onModified(true)
                        }}
                    />
                    <FormHelperText>
                        <HelperText>
                            <HelperTextItem variant={name !== null && name.trim().length > 0 ? "default" : "error"}>{
                                name !== null && name.trim().length > 0 ? "Test names must be unique" : "Name must be unique and not empty"
                            }</HelperTextItem>
                        </HelperText>
                    </FormHelperText>
                </FormGroup>
                <FormGroup label="Datastore" fieldId="datastoreId">
                    <FormSelect
                        value={datastoreId?.toString()}
                        onChange={handleOptionChange}
                        id="horizontal-form-datastore-type"
                        name="horizontal-form-datastore-type"
                        aria-label="Backend Type"
                    >
                        {datastores.map((datastore, index) => (
                            <FormSelectOption key={index} value={datastore.id} label={datastore.name}/>
                        ))}
                    </FormSelect>
                </FormGroup>

                <FormGroup label="Folder" fieldId="folder">
                    <FolderSelect folder={folder} onChange={setFolder} canCreate={true} readOnly={!isTester} placeHolder={"Horreum"}/>
                </FormGroup>
                <FormGroup label="Description" fieldId="description">
                    <TextArea
                        value={description || ""}
                        type="text"
                        id="description"
                        aria-describedby="description-helper"
                        name="description"
                        readOnly={!isTester}
                        onChange={(_event, desc) => {
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
                        onChange={(_event, value) => {
                            setNotificationsEnabled(value)
                            onModified(true)
                        }}
                    />
                </FormGroup>
                <FormGroup
                    label="Compare URL"
                    fieldId="compareUrl"
                >
                    <TextInput
                        value={compareUrl || ""}
                        isRequired={false}
                        type="text"
                        id="compareUrl"
                        aria-describedby="compareUrl-helper"
                        name="compareUrl"
                        //isReadOnly={!isTester} this is no longe rsupport
                        readOnlyVariant={isTester ? undefined : "default"}
                        validated={isUrlValid ? "default" : "error"}
                        onChange={(_event, n) => {
                            setCompareUrl(n)
                            onModified(true)
                        }}
                    />
                    {/* <OptionalFunction
                        readOnly={!isTester}
                        func={compareUrl}
                        defaultFunc="(ids, token) => 'http://example.com/compare?ids=' + ids.join(',')"
                        addText="Add compare function..."
                        undefinedText="Compare function is not defined"
                        onChange={value => {
                            setCompareUrl(value)
                            onModified(true)
                        }}
                    /> */}
                    <FormHelperText>
                        <HelperText>
                            <HelperTextItem>Horreum will append the selected run ids as id=runId&id=otherRunId to the provided url</HelperTextItem>
                            {isUrlValid ? undefined : <HelperTextItem variant="error">Cannot create a valid URL from the provided compare URL</HelperTextItem>}
                        </HelperText>
                    </FormHelperText>
                </FormGroup>
            </Form>
        </>
    )
}
