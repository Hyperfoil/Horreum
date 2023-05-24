import { useState, useMemo, useEffect } from "react"

import { useDispatch, useSelector } from "react-redux"
import { useHistory } from "react-router"

import {
    Button,
    Card,
    CardHeader,
    CardBody,
    Dropdown,
    DropdownToggle,
    DropdownItem,
    Modal,
    PageSection,
    Spinner,
} from "@patternfly/react-core"
import { NavLink } from "react-router-dom"
import { EyeIcon, EyeSlashIcon, FolderOpenIcon } from "@patternfly/react-icons"

import {
    fetchSummary,
    updateAccess,
    deleteTest,
    allSubscriptions,
    addUserOrTeam,
    removeUserOrTeam,
    updateFolder,
} from "./actions"
import * as selectors from "./selectors"

import Table from "../../components/Table"
import AccessIcon from "../../components/AccessIcon"
import ActionMenu, { MenuItem, ActionMenuProps, useChangeAccess } from "../../components/ActionMenu"
import ButtonLink from "../../components/ButtonLink"
import TeamSelect, { Team, ONLY_MY_OWN } from "../../components/TeamSelect"
import FolderSelect from "../../components/FolderSelect"
import FoldersTree from "./FoldersTree"
import ConfirmTestDeleteModal from "./ConfirmTestDeleteModal"
import RecalculateDatasetsModal from "./RecalculateDatasetsModal"
import TestImportButton from "./TestImportButton"

import { Access, isAuthenticatedSelector, useTester, teamToName, teamsSelector, userProfileSelector } from "../../auth"
import { CellProps, Column, UseSortByColumnOptions } from "react-table"
import { TestStorage, TestDispatch } from "./reducers"
import { noop } from "../../utils"

type WatchDropdownProps = {
    id: number
    watching?: string[]
}

const WatchDropdown = ({ id, watching }: WatchDropdownProps) => {
    const [open, setOpen] = useState(false)
    const teams = useSelector(teamsSelector)
    const profile = useSelector(userProfileSelector)
    const dispatch = useDispatch<TestDispatch>()
    if (watching === undefined) {
        return <Spinner size="sm" />
    }
    const personalItems = []
    const self = profile?.username || "__self"
    const isOptOut = watching.some(u => u.startsWith("!"))
    if (watching.some(u => u === profile?.username)) {
        personalItems.push(
            <DropdownItem key="__self" onClick={() => dispatch(removeUserOrTeam(id, self)).catch(noop)}>
                Stop watching personally
            </DropdownItem>
        )
    } else {
        personalItems.push(
            <DropdownItem key="__self" onClick={() => dispatch(addUserOrTeam(id, self)).catch(noop)}>
                Watch personally
            </DropdownItem>
        )
    }
    if (isOptOut) {
        personalItems.push(
            <DropdownItem key="__optout" onClick={() => dispatch(removeUserOrTeam(id, "!" + self)).catch(noop)}>
                Resume watching per team settings
            </DropdownItem>
        )
    } else if (watching.some(u => u.endsWith("-team"))) {
        personalItems.push(
            <DropdownItem key="__optout" onClick={() => dispatch(addUserOrTeam(id, "!" + self)).catch(noop)}>
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
                <DropdownToggle toggleIndicator={null} onToggle={setOpen}>
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
                    <DropdownItem key={team} onClick={() => dispatch(removeUserOrTeam(id, team)).catch(noop)}>
                        Stop watching as team {teamToName(team)}
                    </DropdownItem>
                ) : (
                    <DropdownItem key={team} onClick={() => dispatch(addUserOrTeam(id, team)).catch(noop)}>
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
}

function useDelete(config: DeleteConfig): MenuItem<DeleteConfig> {
    const [confirmDeleteModalOpen, setConfirmDeleteModalOpen] = useState(false)
    const dispatch = useDispatch<TestDispatch>()
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
                            dispatch(deleteTest(props.id)).catch(noop)
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
                <FolderSelect canCreate={true} folder={newFolder} onChange={setNewFolder} readOnly={moving} />
            </Modal>
        ),
    }
}

export function useMoveToFolder(config: MoveToFolderConfig): MenuItem<MoveToFolderConfig> {
    return [MoveToFolderProvider, config]
}

export default function AllTests() {
    const history = useHistory()
    const params = new URLSearchParams(history.location.search)
    const [folder, setFolder] = useState(params.get("folder"))
    const [reloadCounter, setReloadCounter] = useState(0)

    document.title = "Tests | Horreum"
    const dispatch = useDispatch<TestDispatch>()
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
                        <NavLink to={`/run/dataset/list/${data[index].id}`}>
                            {value === undefined ? "(unknown)" : value}&nbsp;
                            <FolderOpenIcon />
                        </NavLink>
                    )
                },
            },
            {
                Header: "Runs",
                accessor: "runs",
                Cell: (arg: C) => {
                    const {
                        cell: {
                            value,
                            row: { index },
                        },
                        data,
                    } = arg
                    return (
                        <NavLink to={`/run/list/${data[index].id}`}>
                            {value === undefined ? "(unknown)" : value}&nbsp;
                            <FolderOpenIcon />
                        </NavLink>
                    )
                },
            },
            { Header: "Owner", accessor: "owner", Cell: (arg: C) => teamToName(arg.cell.value) },
            {
                Header: "Access",
                accessor: "access",
                Cell: (arg: C) => <AccessIcon access={arg.cell.value} />,
            },
            {
                Header: "Actions",
                id: "actions",
                accessor: "id",
                Cell: (arg: C) => {
                    const changeAccess = useChangeAccess({
                        onAccessUpdate: (id: number, owner: string, access: Access) => {
                            dispatch(updateAccess(id, owner, access)).catch(noop)
                        },
                    })
                    const move = useMoveToFolder({
                        name: arg.row.original.name,
                        folder: folder || "",
                        onMove: (id, newFolder) => dispatch(updateFolder(id, folder || "", newFolder)),
                    })
                    const del = useDelete({
                        name: arg.row.original.name,
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
        [dispatch, folder]
    )

    // This selector causes re-render on any state update as the returned list is always new.
    // We would need deepEquals for a proper comparison - the selector combines tests and watches
    // and modifies the Test objects - that wouldn't trigger shallowEqual, though
    const allTests = useSelector(selectors.all)
    const teams = useSelector(teamsSelector)
    const isAuthenticated = useSelector(isAuthenticatedSelector)
    const [rolesFilter, setRolesFilter] = useState<Team>(ONLY_MY_OWN)
    useEffect(() => {
        dispatch(fetchSummary(rolesFilter.key, folder || undefined)).catch(noop)
    }, [dispatch, teams, rolesFilter, folder, reloadCounter])
    useEffect(() => {
        if (isAuthenticated) {
            dispatch(allSubscriptions(folder || undefined)).catch(noop)
        }
    }, [dispatch, isAuthenticated, rolesFilter, folder, reloadCounter])
    if (isAuthenticated) {
        columns = [watchingColumn, ...columns]
    }

    const isTester = useTester()
    const isLoading = useSelector(selectors.isLoading)
    return (
        <PageSection>
            <Card>
                <CardHeader>
                    {isTester && (
                        <>
                            <ButtonLink to="/test/_new">New Test</ButtonLink>
                            <TestImportButton
                                tests={allTests || []}
                                onImported={() => setReloadCounter(reloadCounter + 1)}
                            />
                        </>
                    )}
                    {isAuthenticated && (
                        <div style={{ width: "200px", marginLeft: "16px" }}>
                            <TeamSelect
                                includeGeneral={true}
                                selection={rolesFilter}
                                onSelect={selection => {
                                    setRolesFilter(selection)
                                }}
                            />
                        </div>
                    )}
                </CardHeader>
                <CardBody style={{ overflowX: "auto" }}>
                    <FoldersTree
                        folder={folder || ""}
                        onChange={f => {
                            setFolder(f)
                            history.replace(f ? `/test?folder=${f}` : "/test")
                        }}
                    />
                    <Table
                        columns={columns}
                        data={allTests || []}
                        isLoading={isLoading}
                        sortBy={[{ id: "name", desc: false }]}
                    />
                </CardBody>
            </Card>
        </PageSection>
    )
}
