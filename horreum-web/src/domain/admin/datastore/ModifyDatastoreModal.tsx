import React, {useContext, useState} from "react"

import {
    Button, Form,
    FormGroup, FormSelect, FormSelectOption,
    HelperText,
    HelperTextItem,
    FormHelperText,
    Modal, TextInput
} from "@patternfly/react-core"
import {
    Datastore,
    DatastoreTypeEnum, ElasticsearchDatastoreConfig,
} from "../../../api";
import {AppContext} from "../../../context/appContext";
import {AppContextType} from "../../../context/@types/appContextTypes";

type ConfirmDeleteModalProps = {
    isOpen: boolean
    dataStore: Datastore
    onClose(): void
    onDelete(): Promise<any>
    updateDatastore(datastore: Datastore): void
    persistDatastore: (datastore: Datastore) => Promise<void>
    description: string
    extra?: string
}

interface datastoreOption {
    value: DatastoreTypeEnum,
    label: string,
    disabled: boolean,
    urlDisabled: boolean,
    usernameDisable: boolean,
    tokenDisbaled: boolean
}

export default function ModifyDatastoreModal({isOpen, onClose, persistDatastore, dataStore, updateDatastore}: ConfirmDeleteModalProps) {

    const { alerting } = useContext(AppContext) as AppContextType;
    const [enabledURL, setEnableUrl] = useState(false);
    const [enabledToken, setEnableToken] = useState(false);


    const handleOptionChange = (_event: React.FormEvent<HTMLSelectElement>, value: string) => {
        const option: datastoreOption | undefined = options.filter( optionvalue => optionvalue.value === value).pop()
        if ( option ){
            setEnableUrl(option.urlDisabled)
            setEnableToken(option.tokenDisbaled)

            updateDatastore({...dataStore, type: option.value})
        }
    };

    const saveBackend = () => {
        persistDatastore(dataStore)
            .then( () => {
                onClose();
                alerting.dispatchInfo("SAVE", "Saved!", "Datastore was successfully updated!", 3000)
            })
            .catch(reason => alerting.dispatchError("SAVE", "Saved!", "Failed to save changes to Datastore"))
    }

    const options : datastoreOption[] = [
        { value:  DatastoreTypeEnum.Postgres, label: 'Please select...', disabled: true, urlDisabled: true, usernameDisable: true, tokenDisbaled: true },
        { value:  DatastoreTypeEnum.Elasticsearch, label: 'Elasticsearch', disabled: false, urlDisabled: false, usernameDisable: false, tokenDisbaled: false },
    ];

    const actionButtons = [
        <Button variant="primary" onClick={saveBackend}>Save</Button>,
        <Button variant="link">Cancel</Button>
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
                        {options.map((option, index) => (
                            <FormSelectOption isDisabled={option.disabled} key={index} value={option.value} label={option.label}/>
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
                            const config :ElasticsearchDatastoreConfig = dataStore.config as ElasticsearchDatastoreConfig;
                            config.url = value
                            updateDatastore({...dataStore, config: config})
                            }}
                        isDisabled={enabledURL}
                        type="text"
                        id="horizontal-form-url"
                        aria-describedby="horizontal-form-name-helper"
                        name="horizontal-form-url"
                    />
                    <FormHelperText>
                        <HelperText>
                            <HelperTextItem>Please provide the full host URL to for the datastore service</HelperTextItem>
                        </HelperText>
                    </FormHelperText>
                </FormGroup>
                <FormGroup
                    label="Api Key"
                    fieldId="horizontal-form-token"
                >
                    <TextInput
                        value={"apiKey" in dataStore.config ? dataStore.config.apiKey : ""}
                        onChange={(_, value) => {
                            const config :ElasticsearchDatastoreConfig = dataStore.config as ElasticsearchDatastoreConfig;
                            config.apiKey = value
                            updateDatastore({...dataStore, config: config})
                        }}isDisabled={enabledToken}
                        type="text"
                        id="horizontal-form-api-key"
                        aria-describedby="horizontal-form-token-helper"
                        name="horizontal-form-token"
                    />
                    <FormHelperText>
                        <HelperText>
                            <HelperTextItem>Please provide an API token to authenticate against datastore</HelperTextItem>
                        </HelperText>
                    </FormHelperText>
                </FormGroup>
            </Form>
        </Modal>
    )
}
