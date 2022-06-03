import { useState } from "react"
import { Button, DropdownItem, Modal, Spinner, TextInput, Tooltip } from "@patternfly/react-core"
import { WarningTriangleIcon } from "@patternfly/react-icons"
import moment from "moment"
import { useDispatch } from "react-redux"

import { RenderFunction } from "../tests/reducers"

import { RunsDispatch } from "./reducers"
import { resetToken, dropToken, updateAccess, trash, updateDescription, recalculateDatasets } from "./actions"
import ActionMenu, {
    ActionMenuProps,
    MenuItem,
    useShareLink,
    useChangeAccess,
    useDelete,
} from "../../components/ActionMenu"
import { formatDateTime, toEpochMillis, noop } from "../../utils"
import { useTester } from "../../auth"
import { Access, RunSummary } from "../../api"

export function Description(description: string) {
    return (
        <Tooltip content={description}>
            <span
                style={{
                    whiteSpace: "nowrap",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    maxWidth: "300px",
                    display: "inline-block",
                }}
            >
                {description}
            </span>
        </Tooltip>
    )
}

interface StartStop {
    start: number | string | Date
    stop: number | string | Date
}

export const ExecutionTime = (timestamps: StartStop) => (
    <Tooltip
        isContentLeftAligned
        content={
            <table style={{ width: "300px" }}>
                <tbody>
                    <tr>
                        <td>Started:</td>
                        <td>{formatDateTime(timestamps.start)}</td>
                    </tr>
                    <tr>
                        <td>Finished:</td>
                        <td>{formatDateTime(timestamps.stop)}</td>
                    </tr>
                </tbody>
            </table>
        }
    >
        <span>{moment(toEpochMillis(timestamps.stop)).fromNow()}</span>
    </Tooltip>
)

function useRestore(run: RunSummary): MenuItem<RunSummary> {
    const dispatch = useDispatch<RunsDispatch>()
    return [
        (props: ActionMenuProps, isOwner: boolean, close: () => void, run: RunSummary) => {
            return {
                item: (
                    <DropdownItem
                        key="restore"
                        onClick={() => {
                            close()
                            dispatch(trash(run.id, run.testid, false)).catch(noop)
                        }}
                    >
                        Restore
                    </DropdownItem>
                ),
                modal: null,
            }
        },
        run,
    ]
}

function useRecalculateDatasets(run: RunSummary): MenuItem<RunSummary> {
    const dispatch = useDispatch<RunsDispatch>()
    const [recalculating, setRecalculating] = useState(false)
    return [
        (props: ActionMenuProps, isOwner: boolean, close: () => void) => {
            return {
                item: (
                    <DropdownItem
                        key="recalculate"
                        isDisabled={!isOwner || recalculating}
                        onClick={() => {
                            setRecalculating(true)
                            dispatch(recalculateDatasets(props.id, run.testid))
                                .catch(noop)
                                .finally(() => {
                                    setRecalculating(false)
                                    close()
                                })
                        }}
                    >
                        Recalculate datasets {recalculating && <Spinner size="md" />}
                    </DropdownItem>
                ),
                modal: null,
            }
        },
        run,
    ]
}

function useUpdateDescription(run: RunSummary): MenuItem<RunSummary> {
    const [updateDescriptionOpen, setUpdateDescriptionOpen] = useState(false)
    return [
        (props: ActionMenuProps, isOwner: boolean, close: () => void, run: RunSummary) => {
            return {
                item: (
                    <DropdownItem
                        key="updateDescription"
                        onClick={() => {
                            close()
                            setUpdateDescriptionOpen(true)
                        }}
                    >
                        Edit description
                    </DropdownItem>
                ),
                modal: (
                    <UpdateDescriptionModal
                        key="updateDescription"
                        isOpen={updateDescriptionOpen}
                        onClose={() => setUpdateDescriptionOpen(false)}
                        run={run}
                    />
                ),
            }
        },
        run,
    ]
}

export function Menu(run: RunSummary) {
    const dispatch = useDispatch<RunsDispatch>()

    const shareLink = useShareLink({
        token: run.token || undefined,
        tokenToLink: (id, token) => "/run/" + id + "?token=" + token,
        onTokenReset: id => dispatch(resetToken(id, run.testid)).catch(noop),
        onTokenDrop: id => dispatch(dropToken(id, run.testid)).catch(noop),
    })
    const changeAccess = useChangeAccess({
        onAccessUpdate: (id, owner, access) => dispatch(updateAccess(id, run.testid, owner, access)).catch(noop),
    })
    const del = useDelete({
        onDelete: id => dispatch(trash(id, run.testid)).catch(noop),
    })
    const recalculate = useRecalculateDatasets(run)
    const restore = useRestore(run)
    const menuItems: MenuItem<any>[] = [shareLink, changeAccess, recalculate]
    menuItems.push(run.trashed ? restore : del)

    const isTester = useTester(run.owner)
    const updateDescription = useUpdateDescription(run)
    if (isTester) {
        menuItems.push(updateDescription)
    }

    return (
        <ActionMenu
            id={run.id}
            description={"run " + run.id}
            owner={run.owner}
            access={run.access as Access}
            items={menuItems}
        />
    )
}

type UpdateDescriptionModalProps = {
    isOpen: boolean
    onClose(): void
    run: RunSummary
}

export function UpdateDescriptionModal({ isOpen, onClose, run }: UpdateDescriptionModalProps) {
    const [value, setValue] = useState(run.description)
    const [updating, setUpdating] = useState(false)
    const dispatch = useDispatch<RunsDispatch>()

    return (
        <Modal variant="small" title="UpdateDescription" isOpen={isOpen} onClose={onClose}>
            <TextInput
                value={value}
                type="text"
                id="description"
                name="description"
                onChange={setValue}
                isReadOnly={updating}
            />
            <Button
                variant="primary"
                onClick={() => {
                    setUpdating(true)
                    dispatch(updateDescription(run.id, run.testid, value || ""))
                        .catch(_ => {
                            setValue(run.description)
                        })
                        .finally(() => {
                            setUpdating(false)
                            onClose()
                        })
                }}
            >
                Save
            </Button>
            <Button
                variant="secondary"
                onClick={() => {
                    setValue(run.description)
                    setUpdating(false)
                    onClose()
                }}
            >
                Cancel
            </Button>
        </Modal>
    )
}

export function renderCell(
    render: string | RenderFunction | undefined,
    sub: string | undefined,
    token: string | undefined
) {
    return (arg: any) => {
        const {
            cell: {
                value: cellValue,
                row: { index },
            },
            data,
            column,
        } = arg
        let value = cellValue
        if (sub && value && typeof value === "object") {
            value = value[sub]
        }
        if (!render) {
            if (value === null || value === undefined) {
                return "--"
            } else if (typeof value === "object") {
                return JSON.stringify(value)
            } else if (typeof value === "string" && (value.startsWith("http://") || value.startsWith("https://"))) {
                return (
                    <a href={value} target="_blank ">
                        {value}
                    </a>
                )
            }
            return value
        } else if (typeof render === "string") {
            return (
                <Tooltip content={"Render failure: " + render}>
                    <WarningTriangleIcon style={{ color: "#a30000" }} />
                </Tooltip>
            )
        }
        const useValue = value === null || value === undefined ? (data[index] as any)[column.id.toLowerCase()] : value
        try {
            const rendered = render(useValue, data[index], token)
            if (!rendered) {
                return "--"
            } else if (typeof rendered === "string") {
                //this is a hacky way to see if it looks like html :)
                if (rendered.trim().startsWith("<") && rendered.trim().endsWith(">")) {
                    //render it as html
                    return <div dangerouslySetInnerHTML={{ __html: rendered }} />
                } else {
                    return rendered
                }
            } else if (typeof rendered === "object") {
                return JSON.stringify(rendered)
            } else {
                return rendered + ""
            }
        } catch (e) {
            console.warn("Error in render function %s trying to render %O: %O", render.toString(), useValue, e)
            return "--"
        }
    }
}
