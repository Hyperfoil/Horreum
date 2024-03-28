import {useContext, useEffect, useMemo, useState} from "react"

import {useSelector} from "react-redux"

import {
    Button,
    Card,
    CardBody,
    CardFooter,
    CardHeader,
    Flex,
    FlexItem,
    Modal,
    PageSection,
    Pagination,
    Spinner
} from '@patternfly/react-core';
import {Dropdown, DropdownItem, DropdownToggle} from '@patternfly/react-core/deprecated';
import {NavLink, useLocation, useNavigate} from "react-router-dom"
import {EyeIcon, EyeSlashIcon, FolderOpenIcon} from "@patternfly/react-icons"

import Table from "../../components/Table"
import ActionMenu, {ActionMenuProps, MenuItem, useChangeAccess} from "../../components/ActionMenu"
import ButtonLink from "../../components/ButtonLink"
import TeamSelect, {ONLY_MY_OWN, Team} from "../../components/TeamSelect"
import FolderSelect from "../../components/FolderSelect"
import ConfirmTestDeleteModal from "./ConfirmTestDeleteModal"
import RecalculateDatasetsModal from "./RecalculateDatasetsModal"

import {isAuthenticatedSelector, teamsSelector, teamToName, userProfileSelector, useTester} from "../../auth"
import {CellProps, Column, UseSortByColumnOptions} from "react-table"
import {noop} from "../../utils"
import {
    Access,
    addUserOrTeam,
    deleteTest,
    fetchTestsSummariesByFolder,
    mapTestSummaryToTest,
    removeUserOrTeam,
    SortDirection,
    TestStorage,
    updateAccess,
    updateFolder,
    fetchFolders
} from "../../api"
import AccessIcon from "../../components/AccessIcon"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import FoldersDropDown from "../../components/FoldersDropdown";

type WatchDropdownProps = {
    id: number
    watching?: string[]
}

const DEFAULT_FOLDER: string = "";

const WatchDropdown = ({ id, watching }: WatchDropdownProps) => {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [open, setOpen] = useState(false)
    const teams = useSelector(teamsSelector)
    const profile = useSelector(userProfileSelector)
    if (watching === undefined) {
        return <Spinner size="sm" />
    }
    const personalItems = []
    const self = profile?.username || "__self"
    const isOptOut = watching.some(u => u.startsWith("!"))
    if (watching.some(u => u === profile?.username)) {
        personalItems.push(
            <DropdownItem key="__self" onClick={() => removeUserOrTeam(id, self, alerting).catch(noop)}>
                Stop watching personally
            </DropdownItem>
        )
    } else {
        personalItems.push(
            <DropdownItem key="__self" onClick={() => addUserOrTeam(id, self, alerting).catch(noop)}>
                Watch personally
            </DropdownItem>
        )
    }
    if (isOptOut) {
        personalItems.push(
            <DropdownItem key="__optout" onClick={() => removeUserOrTeam(id, "!" + self, alerting).catch(noop)}>
                Resume watching per team settings
            </DropdownItem>
        )
    } else if (watching.some(u => u.endsWith("-team"))) {
        personalItems.push(
            <DropdownItem key="__optout" onClick={() => addUserOrTeam(id, "!" + self, alerting).catch(noop)}>
                Opt-out of all notifications
            </DropdownItem>
        )
    }
    return (
        <Dropdown
            isOpen={open}
            isPlain
            onSelect={_ => setOpen(false)}
            menuAppendTo={() => document.body}
            toggle={
                <DropdownToggle toggleIndicator={null} onToggle={(_event, val) => setOpen(val)}>
                    {!isOptOut && (
                        <EyeIcon
                            className="watchIcon"
                            style={{ cursor: "pointer", color: watching.length > 0 ? "#151515" : "#d2d2d2" }}
                        />
                    )}
                    {isOptOut && <EyeSlashIcon className="watchIcon" style={{ cursor: "pointer", color: "#151515" }} />}
                </DropdownToggle>
            }
        >
            {personalItems}
            {teams.map(team =>
                watching.some(u => u === team) ? (
                    <DropdownItem key={team} onClick={() => removeUserOrTeam(id, team, alerting).catch(noop)}>
                        Stop watching as team {teamToName(team)}
                    </DropdownItem>
                ) : (
                    <DropdownItem key={team} onClick={() => addUserOrTeam(id, team, alerting).catch(noop)}>
                        Watch as team {teamToName(team)}
                    </DropdownItem>
                )
            )}
        </Dropdown>
    )
}

type C = CellProps<TestStorage>
type Col = Column<TestStorage> & UseSortByColumnOptions<TestStorage>

function useRecalculate(): MenuItem<undefined> {
    const [modalOpen, setModalOpen] = useState(false)
    return [
        (props: ActionMenuProps, isOwner: boolean, close: () => void) => {
            return {
                item: (
                    <DropdownItem
                        key="recalculate"
                        onClick={() => {
                            close()
                            setModalOpen(true)
                        }}
                        isDisabled={!isOwner}
                    >
                        Recalculate datasets
                    </DropdownItem>
                ),
                modal: (
                    <RecalculateDatasetsModal
                        key="recalculate"
                        isOpen={modalOpen}
                        onClose={() => setModalOpen(false)}
                        testId={props.id}
                    />
                ),
            }
        },
        undefined,
    ]
}

type DeleteConfig = {
    name: string
    afterDelete(): void
}

function useDelete(config: DeleteConfig): MenuItem<DeleteConfig> {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [confirmDeleteModalOpen, setConfirmDeleteModalOpen] = useState(false)
    return [
        (props: ActionMenuProps, isOwner: boolean, close: () => void, config: DeleteConfig) => {
            return {
                item: (
                    <DropdownItem
                        key="delete"
                        onClick={() => {
                            close()
                            setConfirmDeleteModalOpen(true)
                        }}
                        isDisabled={!isOwner}
                    >
                        Delete
                    </DropdownItem>
                ),
                modal: (
                    <ConfirmTestDeleteModal
                        key="delete"
                        isOpen={confirmDeleteModalOpen}
                        onClose={() => setConfirmDeleteModalOpen(false)}
                        onDelete={() => {
                            deleteTest(props.id, alerting).then(() => {config.afterDelete()})
                        }}
                        testId={props.id}
                        testName={config.name}
                    />
                ),
            }
        },
        config,
    ]
}

type MoveToFolderConfig = {
    name: string
    folder: string
    onMove(id: number, folder: string): Promise<any>
}

function MoveToFolderProvider(props: ActionMenuProps, isOwner: boolean, close: () => void, config: MoveToFolderConfig) {
    const [isOpen, setOpen] = useState(false)
    const [newFolder, setNewFolder] = useState<string>(config.folder)
    const [moving, setMoving] = useState(false)

    useEffect(() => {
        setNewFolder(config.folder)
    }, [config.folder])

    return {
        item: (
            <DropdownItem
                key="moveToFolder"
                isDisabled={!isOwner}
                onClick={() => {
                    close()
                    setOpen(true)
                }}
            >
                Move to another folder
            </DropdownItem>
        ),
        modal: (
            <Modal
                key="moveToFolder"
                title={`Move test ${config.name} from ${config.folder || "H431orreum"} to another folder`}
                isOpen={isOpen}
                onClose={() => setOpen(false)}
                actions={[
                    <Button
                        key="move"
                        isDisabled={moving || config.folder === newFolder}
                        onClick={() => {
                            setMoving(true)
                            config.onMove(props.id, newFolder).finally(() => {
                                setMoving(false)
                                setOpen(false)
                            })
                        }}
                    >
                        Move
                        {moving && (
                            <>
                                {"\u00A0"}
                                <Spinner size="md" />
                            </>
                        )}
                    </Button>,
                    <Button key="cancel" isDisabled={moving} variant="secondary" onClick={() => setOpen(false)}>
                        Cancel
                    </Button>,
                ]}
            >
                Please select folder:
                <br />
                <FolderSelect canCreate={true} folder={newFolder} onChange={setNewFolder} readOnly={moving} placeHolder={"Horreum"} />
            </Modal>
        ),
    }
}

export function useMoveToFolder(config: MoveToFolderConfig): MenuItem<MoveToFolderConfig> {
    return [MoveToFolderProvider, config]
}

export default function AllTests() {
    const { alerting } = useContext(AppContext) as AppContextType;
    const navigate = useNavigate()
    const location = useLocation()
    const params = new URLSearchParams(location.search)
    const [folder, setFolder] = useState( "folder" in params ? params.get("folder") ?? DEFAULT_FOLDER : DEFAULT_FOLDER)

    document.title = "Tests | Horreum"
    const watchingColumn: Col = {
        Header: "Watching",
        accessor: "watching",
        disableSortBy: true,
        Cell: (arg: C) => {
            return <WatchDropdown watching={arg.cell.value} id={arg.row.original.id} />
        },
    }

    let columns: Col[] = useMemo(
        () => [
            {
                Header: "Name",
                accessor: "name",
                disableSortBy: false,
                Cell: (arg: C) => <NavLink to={`/test/${arg.row.original.id}`}>{arg.cell.value}</NavLink>,
            },
            { Header: "Description", accessor: "description" },
            {
                Header: "Datasets",
                accessor: "datasets",
                Cell: (arg: C) => {
                    const {
                        cell: {
                            value,
                            row: { index },
                        },
                        data,
                    } = arg
                    return (
                        <NavLink to={`/test/${data[index].id}#data`}>
                            {value === undefined ? "(unknown)" : value}&nbsp;
                            <FolderOpenIcon />
                        </NavLink>
                    )
                },
            },
            {
                Header: "Owner",
                id: "owner",
                accessor: (row: TestStorage) => ({
                    owner: row.owner,
                    access: row.access,
                }),
                Cell: (arg: C) => (
                    <>
                        {teamToName(arg.cell.value.owner)}
                        <span style={{ marginLeft: '8px' }}>
                            <AccessIcon access={arg.cell.value.access} showText={false} />
                        </span>
                    </>
                ),
            },
            {
                Header: "Actions",
                id: "actions",
                accessor: "id",
                Cell: (arg: C) => {
                    const changeAccess = useChangeAccess({
                        onAccessUpdate: (id: number, owner: string, access: Access) => {
                            updateAccess(id, owner, access, alerting).then(() => loadTests())
                        },
                    })
                    const move = useMoveToFolder({
                        name: arg.row.original.name,
                        folder: pagination.folder || DEFAULT_FOLDER,
                        onMove: (id, newFolder) => updateFolder(id, pagination.folder, newFolder, alerting).then(loadTests),
                    })
                    const del = useDelete({
                        name: arg.row.original.name,
                        afterDelete: () => loadTests(),
                    })
                    const recalc = useRecalculate()
                    return (
                        <ActionMenu
                            id={arg.cell.value}
                            access={arg.row.original.access as Access}
                            owner={arg.row.original.owner}
                            description={"test " + arg.row.original.name}
                            items={[changeAccess, move, del, recalc]}
                        />
                    )
                },
            },
        ],
        [ folder]
    )

    const [allTests, setTests] = useState<TestStorage[]>([])
    const teams = useSelector(teamsSelector)
    const isAuthenticated = useSelector(isAuthenticatedSelector)
    const [rolesFilter, setRolesFilter] = useState<Team>(ONLY_MY_OWN)

    const [loading, setLoading] = useState(false)
    const [limit, setLimit] = useState(20)
    const [page, setPage] = useState(1)
    const [direction] = useState<SortDirection>("Ascending")
    const pagination = useMemo(() => ({ page, limit, direction, folder }), [page, limit, direction, folder])
    const [count, setCount] = useState(0)

    const [folders, setFolders] = useState<string[]>([])

    const loadTests = () => {
        fetchTestsSummariesByFolder(alerting, SortDirection.Ascending, pagination.folder, pagination.limit, pagination.page, rolesFilter.key, )
            .then(summary => setTests(summary.tests?.map(t => mapTestSummaryToTest(t)) || []))
        fetchFolders(alerting).then(setFolders)
    }
    useEffect(() => {
        loadTests()
    } , [isAuthenticated, teams, rolesFilter, pagination])
    if (isAuthenticated) {
        columns = [watchingColumn, ...columns]
    }

    const isTester = useTester()

    return (
        <PageSection>
            <Card>
                <CardHeader>
                    <Flex>
                        <FlexItem>
                            <FoldersDropDown folders={folders} folder={folder || ""} onChange={f => {
                                setFolder(f)
                                navigate(f ? `/test?folder=${f}` : "/test", { replace: true })
                            }}/>
                        </FlexItem>
                        {isAuthenticated && (
                            <FlexItem>
                                {/* <div style={{ width: "200px"}}> not necessary when insidie a FlexItem?*/}
                                <TeamSelect
                                    includeGeneral={true}
                                    selection={rolesFilter}
                                    onSelect={selection => {
                                        setRolesFilter(selection)
                                    }}
                                />
                                {/* </div> */}
                            </FlexItem>
                        )}

                        {isTester && (
                            <FlexItem align={{ default: 'alignRight' }}>
                                <ButtonLink to="/test/_new#settings">New Test</ButtonLink>
                            </FlexItem>
                        )}

                    </Flex>
                </CardHeader>
                <CardBody style={{ overflowX: "auto" }}>
                    <Table columns={columns}
                        data={allTests || []}
                        isLoading={loading}
                        sortBy={[{ id: "name", desc: false }]}

                    />
                </CardBody>
                <CardFooter style={{ textAlign: "right" }}>
                    <Pagination
                        itemCount={count}
                        perPage={limit}
                        page={page}
                        onSetPage={(e, p) => setPage(p)}
                        onPerPageSelect={(e, pp) => setLimit(pp)}
                    />
                </CardFooter>
            </Card>
        </PageSection>
    )
}
