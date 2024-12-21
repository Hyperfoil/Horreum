import React, {useEffect, useState} from "react"

import {
    Button, Form,
    FormGroup, FormSelect, FormSelectOption,
    HelperText,
    HelperTextItem,
    FormHelperText,
    Modal, TextInput
} from "@patternfly/react-core"
import {
    APIKeyAuth,
    Datastore,
    DatastoreTypeEnum, ElasticsearchDatastoreConfig, TypeConfig, UsernamePassAuth,
} from "../../../api";

type ConfirmDeleteModalProps = {
    isOpen: boolean
    dataStore: Datastore
    dataStoreTypes: Array<TypeConfig>
    onClose(): void
    onDelete(): void
    updateDatastore(datastore: Datastore): void
    persistDatastore: () => Promise<void>
    description: string
    extra?: string
}

type UpdateDatastoreProps = {
    dataStore: Datastore
    updateDatastore(datastore: Datastore): void
}

function NoAuth() {
    return (<></>)
}

function UsernameAuth({dataStore, updateDatastore}: UpdateDatastoreProps) {
    return (
        <>
            <FormGroup
                label="Username"
                fieldId="horizontal-form-token"
            >
                <TextInput
                    value={(dataStore.config.authentication as UsernamePassAuth).username}
                    onChange={(_, value) => {
                        updateDatastore({
                            ...dataStore,
                            config: {
                                ...dataStore.config,
                                authentication: {...dataStore.config.authentication, type: "username", username: value}
                            }
                        })
                    }}
                    type="text"
                    id="horizontal-form-username"
                    aria-describedby="horizontal-form-token-helper"
                    name="horizontal-form-token"
                    isRequired={true}
                />
                <FormHelperText>
                    <HelperText>
                        <HelperTextItem>Please provide a Username to authenticate against datastore</HelperTextItem>
                    </HelperText>
                </FormHelperText>
            </FormGroup>
            <FormGroup
                label="Password"
                fieldId="horizontal-form-token"
            >
                <TextInput
                    value={(dataStore.config.authentication as UsernamePassAuth).password}
                    onChange={(_, value) => {
                        updateDatastore({
                            ...dataStore,
                            config: {
                                ...dataStore.config,
                                authentication: {...dataStore.config.authentication, type: "username", password: value}
                            }
                        })
                    }}
                    type="text"
                    id="horizontal-form-password"
                    aria-describedby="horizontal-form-token-helper"
                    name="horizontal-form-token"
                />
                <FormHelperText>
                    <HelperText>
                        <HelperTextItem>Please provide a Password to authenticate against datastore</HelperTextItem>
                    </HelperText>
                </FormHelperText>
            </FormGroup>
        </>
    )
}

function ApiKeyAuth({dataStore, updateDatastore}: UpdateDatastoreProps) {
    return (
        <>
            <FormGroup
                label="Api Key"
                fieldId="horizontal-form-token"
            >
                <TextInput
                    value={(dataStore.config.authentication as APIKeyAuth).apiKey}
                    onChange={(_, value) => {
                        updateDatastore({
                            ...dataStore,
                            config: {
                                ...dataStore.config,
                                authentication: {...dataStore.config.authentication, type: "api-key", apiKey: value}
                            }
                        })
                    }}
                    type="text"
                    id="horizontal-form-api-key"
                    aria-describedby="horizontal-form-token-helper"
                    name="horizontal-form-token"
                    isRequired={true}
                />
                <FormHelperText>
                    <HelperText>
                        <HelperTextItem>Please provide an API token to authenticate against datastore</HelperTextItem>
                    </HelperText>
                </FormHelperText>
            </FormGroup>
        </>
    )
}

export default function ModifyDatastoreModal({
                                                 isOpen,
                                                 onClose,
                                                 persistDatastore,
                                                 dataStore,
                                                 dataStoreTypes,
                                                 updateDatastore
                                             }: ConfirmDeleteModalProps) {


    const [authOptions, setAuthOptions] = useState<Array<string>>([]);

    useEffect(() => { //find initial auth options for the selected datastore
        setAuthOptions(dataStoreTypes.find(t => t.enumName === dataStore.type)?.supportedAuths || [])
    }, [dataStore]);

    const handleOptionChange = (_event: React.FormEvent<HTMLSelectElement>, value: string) => {
        const selectedOption = dataStoreTypes.find(t => t.enumName === value)
        if (selectedOption) {
            //some ts wizardry to get the enum value from the option name string
            const datastoreTyped = selectedOption.name as keyof typeof DatastoreTypeEnum;
            // let enumKey = Object.keys(DatastoreTypeEnum)[Object.values(DatastoreTypeEnum).indexOf(option.name)];
            updateDatastore({
                ...dataStore,
                type: DatastoreTypeEnum[datastoreTyped],
                config: {...dataStore.config, authentication: {type: 'none'}}
            })
        }
    };

    const handleAuthOptionChange = (_event: React.FormEvent<HTMLSelectElement>, value: string) => {
        switch (value) { //pita, but TS compiler complains need to switch on String value to get the correct union type
            case "none":
                updateDatastore({...dataStore, config: {...dataStore.config, authentication: {type: 'none'}}})
                break;
            case "username":
                updateDatastore({
                    ...dataStore,
                    config: {...dataStore.config, authentication: {type: 'username', username: "", password: ""}}
                })
                break;
            case "api-key":
                updateDatastore({
                    ...dataStore,
                    config: {...dataStore.config, authentication: {type: 'api-key', apiKey: ""}}
                })
                break;
        }
    };

    const actionButtons = [
        <Button variant="primary" onClick={persistDatastore}>Save</Button>,
        <Button variant="link" onClick={onClose}>Cancel</Button>
    ]

    return (
        <Modal variant="medium" title="Modify Datastore" actions={actionButtons} isOpen={isOpen} onClose={onClose}>

            {/*TODO: create dynamic form based from config - see change detection for example*/}
            <Form isHorizontal>
                <FormGroup label="Datastore Type" fieldId="horizontal-form-datastore-type">
                    <FormSelect
                        value={dataStore.type}
                        onChange={handleOptionChange}
                        id="horizontal-form-datastore-type"
                        name="horizontal-form-datastore-type"
                        aria-label="Backend Type"
                    >
                        <FormSelectOption isDisabled={false} key={0} value={''} label={'Please select type'}
                                          placeholder={"true"}/>
                        {dataStoreTypes.filter(type => !type.builtIn).map((option, index) => (
                            <FormSelectOption isDisabled={option.builtIn} key={index} value={option.enumName}
                                              label={option.label || ""}/>
                        ))}
                    </FormSelect>
                </FormGroup>
                <FormGroup
                    label="name"
                    isRequired
                    fieldId="horizontal-form-name"
                >
                    <TextInput
                        value={dataStore.name}
                        onChange={(_, value) => updateDatastore({...dataStore, name: value})}
                        isRequired
                        type="text"
                        id="horizontal-form-name"
                        aria-describedby="horizontal-form-name-helper"
                        name="horizontal-form-name"
                    />
                    <FormHelperText>
                        <HelperText>
                            <HelperTextItem>Please an name for the datastore</HelperTextItem>
                        </HelperText>
                    </FormHelperText>
                </FormGroup>
                <FormGroup
                    label="URL"
                    fieldId="horizontal-form-name"
                >
                    <TextInput
                        value={"url" in dataStore.config ? dataStore.config.url : ""}
                        onChange={(_, value) => {
                            const config: ElasticsearchDatastoreConfig = dataStore.config as ElasticsearchDatastoreConfig;
                            config.url = value
                            updateDatastore({...dataStore, config: config})
                        }}
                        type="text"
                        id="horizontal-form-url"
                        aria-describedby="horizontal-form-name-helper"
                        name="horizontal-form-url"
                    />
                    <FormHelperText>
                        <HelperText>
                            <HelperTextItem>Please provide the full host URL to for the datastore
                                service</HelperTextItem>
                        </HelperText>
                    </FormHelperText>
                </FormGroup>

                <FormGroup label="Authentication Type" fieldId="horizontal-form-auth-type">
                    <FormSelect
                        value={dataStore.config.authentication.type}
                        onChange={handleAuthOptionChange}
                        id="horizontal-form-auth-type"
                        name="horizontal-form-auth-type"
                        aria-label="Authenticaion Type"
                    >
                        {authOptions.map((authOption, index) => (
                            <FormSelectOption key={index} value={authOption} label={authOption}/>
                        ))}
                    </FormSelect>
                </FormGroup>

                {dataStore.config.authentication.type === "none" && (
                    <NoAuth/>)}
                {dataStore.config.authentication.type === "username" && (
                    <UsernameAuth
                        dataStore={dataStore}
                        updateDatastore={updateDatastore}
                    />
                )}
                {dataStore.config.authentication.type === "api-key" && (
                    <ApiKeyAuth
                        dataStore={dataStore}
                        updateDatastore={updateDatastore}
                    />
                )}
            </Form>
        </Modal>
    )
}
