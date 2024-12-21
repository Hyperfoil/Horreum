import {useContext, useEffect, useState} from "react"

import {Button, Form, FormGroup} from "@patternfly/react-core"

import ConfirmDeleteModal from "../../components/ConfirmDeleteModal"
import TeamSelect, {Team, SHOW_ALL, createTeam} from "../../components/TeamSelect"

import {
    Table,
    Thead,
    Tr,
    Th,
    Tbody,
    Td,
    ActionsColumn,
    IAction
} from '@patternfly/react-table';
import {Stack, StackItem} from "@patternfly/react-core";
import ModifyDatastoreModal from "./datastore/ModifyDatastoreModal";
import VerifyBackendModal from "./datastore/VerifyBackendModal";
import {
    Access,
    apiCall,
    CollectorApiDatastoreConfig,
    configApi,
    Datastore,
    DatastoreTypeEnum,
    ElasticsearchDatastoreConfig, TypeConfig
} from "../../api";
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import {useSelector} from "react-redux";
import {defaultTeamSelector} from "../../auth";

interface dataStoreTableProps {
    datastores: Datastore[]
    datastoreTypes: TypeConfig[]
    team: Team
    modifyDatastore: (backend: Datastore) => void
    verifyDatastore: (backend: Datastore) => void
    deleteDatastore: (backend: Datastore) => void

}

const newBackendConfig: ElasticsearchDatastoreConfig | CollectorApiDatastoreConfig = {
    url: "",
    builtIn: false,
    authentication: {'type': 'none'}
}

const DatastoresTable = (props: dataStoreTableProps) => {

    const columnNames = {
        type: 'Type',
        name: 'Name',
        builtIn: 'Built in',
        action: 'Action'

    };

    const defaultActions = (selectedDatastore: Datastore): IAction[] => [
        {
            title: `Edit`, onClick: () => props.modifyDatastore(selectedDatastore)
        },
        {
            title: `Test`, onClick: () => props.verifyDatastore(selectedDatastore)
        },
        {
            isSeparator: true
        },
        {
            title: `Delete`, onClick: () => props.deleteDatastore(selectedDatastore)
        },

    ];

    return (
        <Table aria-label="Datastores table">
            <Thead>
                <Tr>
                    <Th>{columnNames.type}</Th>
                    <Th>{columnNames.name}</Th>
                    <Th>{columnNames.action}</Th>
                    <Th></Th>
                </Tr>
            </Thead>
            <Tbody>
                {props.datastores.map(teamDatastore => {
                    const rowActions: IAction[] | null = defaultActions(teamDatastore);
                    return (
                        <Tr key={teamDatastore.id}>
                            <Td dataLabel={columnNames.type}>{props.datastoreTypes.find((type) => type.enumName === teamDatastore.type)?.label}</Td>
                            <Td dataLabel={columnNames.name}>{teamDatastore.name}</Td>
                            <Td isActionCell>
                                <ActionsColumn
                                    items={rowActions}
                                    isDisabled={teamDatastore.config.builtIn}
                                />
                            </Td>
                        </Tr>)
                })}
            </Tbody>
        </Table>

    );
}

const errorFormatter = (error: any) => {
    // Check if error has a message property
    if (error.message) {
        return error.message;
    }
    // If error is a string, return it as is
    if (typeof error === 'string') {
        return error;
    }
    // If error is an object, stringify it
    if (typeof error === 'object') {
        return JSON.stringify(error);
    }
    // If none of the above, return a generic error message
    return 'An error occurred';
}


export default function Datastores() {
    const {alerting} = useContext(AppContext) as AppContextType;
    const defaultTeam = useSelector(defaultTeamSelector) || SHOW_ALL.key;
    const [datastores, setDatastores] = useState<Datastore[]>([])
    const [datastoreTypes, setDatastoreTypes] = useState<TypeConfig[]>([])
    const [curTeam, setCurTeam] = useState<Team>(createTeam(defaultTeam));

    const newDataStore: Datastore = {
        id: -1,
        name: "",
        owner: curTeam.key,
        access: Access.Private,
        config: newBackendConfig,
        type: DatastoreTypeEnum.Postgres
    }

    const [deleteModalOpen, setDeleteModalOpen] = useState(false);
    const [editModalOpen, setEditModalOpen] = useState<boolean>(false);
    const [verifyModalOpen, setVerifyModalOpen] = useState(false);
    const [datastore, setDatastore] = useState<Datastore>(newDataStore)

    const deleteModalToggle = () => {
        if (datastore.config && !datastore.config.builtIn) {
            setDeleteModalOpen(!deleteModalOpen);
        } else {
            alerting.dispatchError(null, "DELETE", "Can not delete built in datastore")
        }
    };
    const editModalToggle = () => {
        setEditModalOpen(!editModalOpen);
    };
    const verifyModalToggle = () => {
        setVerifyModalOpen(!verifyModalOpen);
    };

    const newDatastore = () => {
        setDatastore(newDataStore)
        setEditModalOpen(!editModalOpen);
    };

    const fetchDataStores = (): Promise<void> => {
        return apiCall(configApi.datastores(curTeam.key), alerting, "FETCH_DATASTORES", "Cannot fetch datastores")
            .then(setDatastores)
    }

    const fetchDataStoreTypes = (): Promise<void> => {
        return apiCall(configApi.datastoreTypes(), alerting, "FETCH_DATASTORE_TYPES", "Cannot fetch Datastore Types")
            .then(setDatastoreTypes)
    }

    const deleteDatastore = (datastore: Datastore): Promise<void> => {
        return apiCall(configApi.deleteDatastore(datastore.id), alerting, "DELETE_BACKEND", "Cannot delete datastore")
            .then(fetchDataStores)
    }

    useEffect(() => {
        fetchDataStores()
    }, [curTeam])

    useEffect(() => {
        fetchDataStoreTypes()
    }, [])


    const updateDatastore = (datastore: Datastore): void => {
        setDatastore(datastore)
    }

    const handleDeleteDatastore = (datastore: Datastore) => {
        setDatastore(datastore)
        deleteModalToggle()
    }
    const handleModifyDatastore = (datastore: Datastore): void => {
        setDatastore(datastore)
        editModalToggle()
    }
    const handleVerifyDatastore = (datastore: Datastore): void => {
        setDatastore(datastore)
        verifyModalToggle()
    }
    const persistDatastore = (): Promise<void> => {

        let apicall: Promise<any>

        if (datastore.id == -1) {
            apicall = apiCall(configApi.newDatastore(datastore), alerting, "NEW_DATASTORE", "Could create new datastore")
        } else {
            apicall = apiCall(configApi.updateDatastore(datastore), alerting, "UPDATE_DATASTORE", "Could create new datastore")
        }

        return apicall
            .then(fetchDataStores)
            .then(editModalToggle)
            .then(() => alerting.dispatchInfo("SAVE", "Saved!", "Datastore was successfully updated!", 3000))
            .catch(reason => alerting.dispatchError(reason, "Saved!", "Failed to save changes to Datastore", errorFormatter))
    }

    return (
        <Form isHorizontal>
            <FormGroup label="Team" fieldId="teamID">
                <TeamSelect
                    includeGeneral={false}
                    selection={curTeam as Team}
                    onSelect={selection => {
                        setCurTeam(selection)
                    }}
                />
            </FormGroup>
            <FormGroup label="Configured Datastores" fieldId="configured">
                <Stack hasGutter>
                    <StackItem>
                        <DatastoresTable
                            datastores={datastores}
                            datastoreTypes={datastoreTypes}
                            team={curTeam}
                            modifyDatastore={handleModifyDatastore}
                            verifyDatastore={handleVerifyDatastore}
                            deleteDatastore={handleDeleteDatastore}
                        />
                    </StackItem>
                    <StackItem>
                        <Button variant={"primary"} id={"newDatastore"} onClick={newDatastore}>New Datastore</Button>
                    </StackItem>
                    <StackItem>
                        <ConfirmDeleteModal
                            key="confirmDelete"
                            description={"this datastore: " + datastore?.name}
                            isOpen={deleteModalOpen}
                            onClose={deleteModalToggle}
                            onDelete={() => {
                                deleteDatastore(datastore)
                                deleteModalToggle()
                                return Promise.resolve()
                            }
                            }
                        />
                    </StackItem>
                    <StackItem>
                        <ModifyDatastoreModal
                            key="editDatastore"
                            description="Modify Datastore"
                            isOpen={editModalOpen}
                            onClose={editModalToggle}
                            persistDatastore={persistDatastore}
                            dataStore={datastore}
                            dataStoreTypes={datastoreTypes}
                            updateDatastore={updateDatastore}
                            onDelete={editModalToggle}
                        />
                    </StackItem>
                    <StackItem>
                        <VerifyBackendModal
                            key="verifyBackend"
                            description="This is a test"
                            isOpen={verifyModalOpen}
                            onClose={verifyModalToggle}
                            onDelete={() => {
                                verifyModalToggle()
                                return Promise.resolve()
                            }
                            }
                        />
                    </StackItem>
                </Stack>
            </FormGroup>
        </Form>
    )
}
