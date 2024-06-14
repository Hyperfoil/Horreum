import {useContext, useEffect, useState} from "react"

import {Button, Form, FormGroup} from "@patternfly/react-core"

import ConfirmDeleteModal from "../../components/ConfirmDeleteModal"
import TeamSelect, {Team, SHOW_ALL} from "../../components/TeamSelect"


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
    ElasticsearchDatastoreConfig
} from "../../api";
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import {noop} from "../../utils";

interface dataStoreTableProps {
    datastores: Datastore[]
    team: Team
    persistDatastore: (backend: Datastore) => Promise<void>
    deleteDatastore: (id: string) => Promise<void>

}

const DatastoresTable = ( props: dataStoreTableProps) => {


    const columnNames = {
        type: 'Type',
        name: 'Name',
        builtIn: 'Built in',
        action: 'Action'

    };

    const defaultActions = (datastore: Datastore): IAction[] => [
        {
            title: `Edit`, onClick: () => editModalToggle(datastore.id)
        },
        {
            title: `Test`, onClick: () => verifyModalToggle(datastore.id)
        },
        {
            isSeparator: true
        },
        {
            title: `Delete`, onClick: () => deleteModalToggle(datastore.id)
        },

    ];
    const newBackendConfig: ElasticsearchDatastoreConfig | CollectorApiDatastoreConfig = {
        url: "",
        apiKey: "",
        builtIn: false
    }

    const newDataStore: Datastore = {
        id: -1,
        name: "",
        owner: props.team.key,
        builtIn: false,
        access: Access.Private,
        config: newBackendConfig,
        type: DatastoreTypeEnum.Postgres
    }

    const [deleteModalOpen, setDeleteModalOpen] = useState(false);
    const [editModalOpen, setEditModalOpen] = useState<boolean>(false);
    const [verifyModalOpen, setVerifyModalOpen] = useState(false);
    const [datastore, setDatastore] = useState<Datastore >(newDataStore)

    const findDatastore = (id: number) => {
        return props.datastores.filter( datastore => datastore.id === id).pop() || newDataStore
    }

    const updateDatastore = ( datastore: Datastore) : void => {
        setDatastore(datastore)
    }

    const deleteModalToggle = (id: number) => {
        setDatastore(findDatastore(id))
        setDeleteModalOpen(!deleteModalOpen);
    };
    const editModalToggle = (id: number) => {
        setDatastore(findDatastore(id))
        setEditModalOpen(!editModalOpen);
    };
    const verifyModalToggle = (id: number) => {
        setDatastore(findDatastore(id))
        setVerifyModalOpen(!verifyModalOpen);
    };

    const newDatastore = () => {
        setDatastore(newDataStore)
        setEditModalOpen(!editModalOpen);
    };


    return (

        <Stack hasGutter>
            <StackItem>
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
                        {props.datastores?.map(repo => {
                            const rowActions: IAction[] | null = defaultActions(repo);
                            return (
                                <Tr key={repo.type}>
                                    <Td dataLabel={columnNames.type}>{repo.type}</Td>
                                    <Td dataLabel={columnNames.name}>{repo.name}</Td>
                                    <Td isActionCell>
                                        <ActionsColumn
                                            items={rowActions}
                                            isDisabled={repo.builtIn}
                                        />
                                    </Td>
                                </Tr>)
                        })}
                    </Tbody>
                </Table>
            </StackItem>
            <StackItem>
                <Button variant={"primary"} id={"newDatastore"} onClick={newDatastore}>New Datastore</Button>
            </StackItem>
            <StackItem>
                <ConfirmDeleteModal
                    key="confirmDelete"
                    description={"this datastore: " + datastore?.name}
                    isOpen={deleteModalOpen}
                    onClose={() => deleteModalToggle(0)}
                    onDelete={() => {
                        props.deleteDatastore(datastore?.id?.toString() || "")
                        deleteModalToggle(0)
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
                    onClose={() => editModalToggle(0)}
                    persistDatastore={props.persistDatastore}
                    dataStore={datastore}
                    updateDatastore={updateDatastore}
                    onDelete={() => {
                        editModalToggle(0)
                        return Promise.resolve()
                    }
                    }
                />
            </StackItem>
            <StackItem>
                <VerifyBackendModal
                    key="verifyBackend"
                    description="This is a test"
                    isOpen={verifyModalOpen}
                    onClose={() => verifyModalToggle(0)}
                    onDelete={() => {
                        verifyModalToggle(0)
                        return Promise.resolve()
                    }
                    }
                />
            </StackItem>
        </Stack>
    );
}

export default function Datastores() {
    const { alerting } = useContext(AppContext) as AppContextType;

    const [datastores, setDatastores] = useState<Datastore[]>([])
    const [curTeam, setCurTeam] = useState<Team>(SHOW_ALL)

    const fetchDataStores = () : Promise<void> => {
        return apiCall(configApi.datastores(curTeam.key), alerting, "FETCH_DATASTORES", "Cannot fetch datastores")
            .then(setDatastores)
        // userApi.administrators().then(
        //     list => setAdmins(list.map(userElement)),
        //     error => dispatchError(dispatch, error, "FETCH ADMINS", "Cannot fetch administrators")
        // )

    }

    const deleteDatastore = (id: string) : Promise<void> => {
        return apiCall(configApi.deleteDatastore(id), alerting, "DELETE_BACKEND", "Cannot delete datastore")
            .then(fetchDataStores)
    }

    useEffect(() => {
        fetchDataStores()
    }, [curTeam])

    const persistNewBackend = (datastore: Datastore) : Promise<void> => {

        let apicall: Promise<any>

        if ( datastore.id == -1){
            apicall = apiCall(configApi.newDatastore(datastore), alerting, "NEW_DATASTORE", "Could create new datastore")
        } else {
            apicall = apiCall(configApi.updateDatastore(datastore), alerting, "UPDATE_DATASTORE", "Could create new datastore")
        }

        return apicall.then(fetchDataStores)
    }

    return (
        <Form isHorizontal>
            <FormGroup label="Team" fieldId="teamID">
                <TeamSelect
                    includeGeneral={false}
                    selection={curTeam}
                    onSelect={selection => {
                        setCurTeam(selection)
                    }}
                />
            </FormGroup>
            <FormGroup label="Configured Datastores" fieldId="configured">
                <DatastoresTable
                    datastores={datastores}
                    team={curTeam}
                    persistDatastore={persistNewBackend}
                    deleteDatastore={deleteDatastore}
                />
            </FormGroup>
        </Form>
    )
}
