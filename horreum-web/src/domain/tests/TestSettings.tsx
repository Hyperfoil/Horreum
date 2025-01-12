import React, { useState, useEffect, useContext } from "react"
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
    TextInput,
    Title,
    Divider,
} from "@patternfly/react-core"
import FolderSelect from "../../components/FolderSelect"
import { TabFunctionsRef } from "../../components/SavedTabs"

import { Test, Access, addTest, configApi, Datastore, apiCall, updateTest } from "../../api"
import { useTester, defaultTeamSelector, teamToName } from "../../auth"
import { AppContext } from "../../context/appContext";
import { AppContextType } from "../../context/@types/appContextTypes";
import TeamSelect from "../../components/TeamSelect"
import AccessChoice from "../../components/AccessChoice"
import AccessIcon from "../../components/AccessIcon"

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
    // general settings
    const [name, setName] = useState("")
    const [folder, setFolder] = useState("")
    const [datastoreId, setDatastoreId] = useState(test?.datastoreId)
    const [description, setDescription] = useState("")
    const [compareUrl, setCompareUrl] = useState<string | undefined>(undefined)
    const [notificationsEnabled, setNotificationsEnabled] = useState(true)
    const [datastores, setDatastores] = useState<Datastore[]>([])
    // permissions
    const [owner, setOwner] = useState(test?.owner || defaultRole || "")
    const [access, setAccess] = useState<Access>(test?.access || Access.Public)

    useEffect( () => {
        apiCall(configApi.getDatastoresByTeam(owner), alerting, "DATASTORE", "Error occurred fetching datastores")
            .then(ds => setDatastores(ds))
    }, [test, owner])

    const updateState = (t?: Test) => {
        setName(t?.name || "")
        setFolder(t?.folder || "")
        setDatastoreId( t?.datastoreId || 1 )
        setDescription(t?.description || "")
        setCompareUrl(t?.compareUrl?.toString() || undefined)
        setNotificationsEnabled(!t || t.notificationsEnabled)
        setOwner(t?.owner || defaultRole || "")
        setAccess(t?.access || Access.Public)
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
        save: async () => {
            const newTest: Test = {
                id: test?.id || 0,
                name,
                folder,
                description,
                datastoreId: datastoreId || 1,
                compareUrl: compareUrl || undefined, // when empty set to undefined
                notificationsEnabled,
                fingerprintLabels: test?.fingerprintLabels || [],
                fingerprintFilter: test?.fingerprintFilter,
                owner: owner || defaultRole || "__test_created_without_a_role__",
                access: access,
                transformers: test?.transformers || [],
            }
            const response = (test?.id && test?.id > 0)
                ? await updateTest(newTest, alerting)
                : await addTest(newTest, alerting)
            if (response.id !== test?.id) {
                return onTestIdChange(response.id)
            }
        },
        reset: () => updateState(test),
    }

    const isTester = useTester(test?.owner)
    const isUrlValid = isUrlOrBlank(compareUrl)
    return (
        <>
            <Form isHorizontal={true}>
                <Title headingLevel="h2">General settings</Title>
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
                <FormGroup label="Team" fieldId="testOwner">
                    {isTester ? (
                        <TeamSelect
                            includeGeneral={false}
                            selection={teamToName(owner) || ""}
                            onSelect={selection => {
                                setOwner(selection.key)
                                onModified(true)
                            }}
                        />
                    ) : (
                        <TextInput value={teamToName(owner) || ""} id="testOwner"  readOnlyVariant="default" />
                    )}
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
                    <FormHelperText>
                        <HelperText>
                            <HelperTextItem>Horreum will append the selected run ids as id=runId&id=otherRunId to the provided url</HelperTextItem>
                            {isUrlValid ? undefined : <HelperTextItem variant="error">Cannot create a valid URL from the provided compare URL</HelperTextItem>}
                        </HelperText>
                    </FormHelperText>
                </FormGroup>

                <Divider/>

                <Title headingLevel="h2">Permissions</Title>

                <FormGroup label="Access rights" fieldId="testAccess">
                    {isTester ? (
                        <AccessChoice
                            checkedValue={access}
                            onChange={a => {
                                setAccess(a)
                                onModified(true)
                            }}
                        />
                    ) : (
                        <AccessIcon access={access} />
                    )}
                </FormGroup>
            </Form>
        </>
    )
}
