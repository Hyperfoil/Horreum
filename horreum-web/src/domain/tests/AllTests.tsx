import React, {useContext, useEffect, useMemo, useState} from "react"

import {useSelector} from "react-redux"

import {
    Breadcrumb,
    BreadcrumbHeading,
    BreadcrumbItem,
    Button,
    Dropdown,
    DropdownItem,
    DropdownList,
    MenuToggle,
    MenuToggleElement,
    Modal,
    PageSection,
    Spinner,
    Toolbar,
    ToolbarContent,
    ToolbarGroup,
    ToolbarItem
} from '@patternfly/react-core';
import {NavLink, useLocation, useNavigate} from "react-router-dom"
import {EyeIcon, EyeSlashIcon, FolderOpenIcon} from "@patternfly/react-icons"

import ActionMenu, {ActionMenuProps, MenuItem, useChangeAccess} from "../../components/ActionMenu"
import ButtonLink from "../../components/ButtonLink"
import TeamSelect, {createTeam, ONLY_MY_OWN, Team} from "../../components/TeamSelect"
import FolderSelect from "../../components/FolderSelect"
import ConfirmTestDeleteModal from "./ConfirmTestDeleteModal"
import RecalculateDatasetsModal from "./RecalculateDatasetsModal"

import {isAuthenticatedSelector, teamsSelector, teamToName, userProfileSelector, useTester} from "../../auth"
import {CellProps, Column, SortingRule, UseSortByColumnOptions} from "react-table"
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
    fetchFolders, testApi, TestExport
} from "../../api"
import AccessIcon from "../../components/AccessIcon"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import FoldersDropDown from "../../components/FoldersDropdown";
import ImportButton from "../../components/ImportButton";
import CustomTable from "../../components/CustomTable";
import FilterSearchInput from "../../components/FilterSearchInput";

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
    const onSelect = () => {
        setOpen(false);
    };

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
            onSelect={onSelect}
            onOpenChange={(isOpen: boolean) => setOpen(isOpen)}
            toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
                <MenuToggle ref={toggleRef} onClick={() => setOpen(!open)} isExpanded={open} variant="plain">
                    {!isOptOut && (
                        <EyeIcon
                            className="watchIcon"
                            style={{ cursor: "pointer", color: watching.length > 0 ? "#151515" : "#d2d2d2" }}
                        />
                    )}
                    {isOptOut && <EyeSlashIcon className="watchIcon" style={{ cursor: "pointer", color: "#151515" }} />}
                </MenuToggle>
            )}
        >
            <DropdownList>
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
            </DropdownList>
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
                title={`Move test ${config.name} from ${config.folder || "Horreum"} to another folder`}
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
    const [folder, setFolder] = useState(params.get("folder") ?? DEFAULT_FOLDER)

    document.title = "Tests | Horreum"
    const watchingColumn: Col = {
        Header: "Watching",
        id: "watching",
        accessor: "watching",
        disableSortBy: true,
        Cell: (arg: C) => {
            return <WatchDropdown watching={arg.cell.value ? Array.from(arg.cell.value) : []} id={arg.row.original.id} />
        },
    }

    let columns: Col[] = useMemo(
        () => [
            {
                Header: "Name",
                id: "name",
                accessor: "name",
                disableSortBy: false,
                Cell: (arg: C) => <NavLink to={`/test/${arg.row.original.id}`}>{arg.cell.value}</NavLink>,
            },
            { 
                Header: "Description", 
                id: "description",
                accessor: "description"
            },
            {
                Header: "Datasets",
                id: "datasets",
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
                disableSortBy: true,
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
    const rolesFilterFromQuery = params.get("filter")
    const [rolesFilter, setRolesFilter] = useState<Team>(rolesFilterFromQuery !== null ? createTeam(rolesFilterFromQuery) : ONLY_MY_OWN)

    const [loading, setLoading] = useState(false)
    const [limit, setLimit] = useState(20)
    const [page, setPage] = useState(1)
    const [sortBy, setSortBy] = useState<SortingRule<TestStorage>>({id: "name", desc: false})
    const pagination = useMemo(() => ({ page, limit, sortBy, folder }), [page, limit, sortBy, folder])
    
    const [count, setCount] = useState(0)
    const [folders, setFolders] = useState<string[]>([])

    const [nameFilter, setNameFilter] = useState<string>("")

    const loadTests = () => {
        setLoading(true)
        const direction = pagination.sortBy.desc ? SortDirection.Descending : SortDirection.Ascending
        fetchTestsSummariesByFolder(alerting, direction, pagination.folder, pagination.limit, pagination.page, rolesFilter.key, nameFilter)
            .then(summary => {
                setTests(summary.tests?.map(t => mapTestSummaryToTest(t)) || [])
                if (summary.count) {
                    setCount(summary.count)
                }
            })
            .finally(() => setLoading(false))
        fetchFolders(alerting).then(setFolders)
    }

    useEffect(() => {
        loadTests()
    } , [isAuthenticated, teams, rolesFilter, pagination, nameFilter])
    
    if (isAuthenticated) {
        columns = [watchingColumn, ...columns]
    }

    useEffect(() => {
        let query = ""
        if (folder) {
            query += "&folder=" + encodeURIComponent(folder)
        }
        if (rolesFilter.key !== ONLY_MY_OWN.key) {
            query += "&filter=" + rolesFilter.key
        }
        query = "?" + query.replace(/^&/, '');

        navigate(location.pathname + query)
    }, [folder, rolesFilter])

    const isTester = useTester()

    return (
        <PageSection>
            <Toolbar>
                <ToolbarContent>
                    <ToolbarItem>
                        <Breadcrumb>
                            <BreadcrumbItem isDropdown>
                                <FoldersDropDown folders={folders} folder={folder} onChange={f => {
                                    setFolder(f)
                                }}/>
                            </BreadcrumbItem>
                            {folder && (
                                <BreadcrumbHeading>{folder}</BreadcrumbHeading>
                            )}
                        </Breadcrumb>
                    </ToolbarItem>
                </ToolbarContent>
            </Toolbar>
            <Toolbar>
                <ToolbarContent>
                    <ToolbarGroup variant="button-group">
                        {isAuthenticated && (
                            <ToolbarItem>
                                <TeamSelect
                                    includeGeneral={true}
                                    selection={rolesFilter}
                                    onSelect={selection => {
                                        setRolesFilter(selection)
                                    }}
                                />
                            </ToolbarItem>
                        )}
                        <ToolbarItem>
                            <FilterSearchInput 
                                placeholder="Filter by name"
                                onSearchBy={setNameFilter}
                                onClearBy={() => setNameFilter("")}
                            />
                        </ToolbarItem>
                    </ToolbarGroup>
                    {isTester && (
                        <ToolbarGroup variant="button-group" align={{ default: 'alignRight' }}>
                            <ToolbarItem>
                                <ImportButton
                                    label="Import test"
                                    onLoad={config => {
                                        const overridden = allTests.find(t => t.id === config?.id)
                                        return overridden ? (
                                            <>
                                                This configuration is going to override test {overridden.name} ({overridden.id})
                                                {config?.name !== overridden.name && ` using new name ${config?.name}`}.<br />
                                                <br />
                                                Do you really want to proceed?
                                            </>
                                        ) : null
                                    }}
                                    onImport={config => {
                                        return config.id != null && config.id > 0 ?
                                            testApi.updateTest(config as TestExport) :
                                            testApi.importTest(config as TestExport)
                                    }}
                                    onImported={() => loadTests()}
                                />
                            </ToolbarItem>
                            <ToolbarItem>
                                <ButtonLink to="/test/_new#settings">New Test</ButtonLink>
                            </ToolbarItem>
                        </ToolbarGroup>
                    )}
                </ToolbarContent>
            </Toolbar>
            <CustomTable<TestStorage>
                columns={columns}
                data={allTests || []}
                isLoading={loading}
                sortBy={[sortBy]}
                onSortBy={order => {
                    if (order.length > 0 && order[0]) {
                        setSortBy(order[0])
                    }
                }}
                cellModifier="wrap"
                pagination={{
                    bottom: true,
                    count: count,
                    perPage: limit,
                    page: page,
                    onSetPage: (e, p) => setPage(p),
                    onPerPageSelect: (e, pp) => setLimit(pp)
                }}
            />
        </PageSection>
    )
}
