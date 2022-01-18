import { useState } from "react"
import { Button, Chip, DropdownItem, Modal, TextInput, Tooltip } from "@patternfly/react-core"
import moment from "moment"
import { useDispatch } from "react-redux"

import { Run, RunsDispatch } from "./reducers"
import { resetToken, dropToken, updateAccess, trash, updateDescription } from "./actions"
import ActionMenu, {
    ActionMenuProps,
    MenuItem,
    useShareLink,
    useChangeAccess,
    useDelete,
} from "../../components/ActionMenu"
import { formatDateTime, toEpochMillis, noop } from "../../utils"
import { useTester } from "../../auth"

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

export const ExecutionTime = (run: Run) => (
    <Tooltip
        isContentLeftAligned
        content={
            <table style={{ width: "300px" }}>
                <tbody>
                    <tr>
                        <td>Started:</td>
                        <td>{formatDateTime(run.start)}</td>
                    </tr>
                    <tr>
                        <td>Finished:</td>
                        <td>{formatDateTime(run.stop)}</td>
                    </tr>
                </tbody>
            </table>
        }
    >
        <span>{moment(toEpochMillis(run.stop)).fromNow()}</span>
    </Tooltip>
)

function useRestore(run: Run): MenuItem<Run> {
    const dispatch = useDispatch<RunsDispatch>()
    return [
        (props: ActionMenuProps, isOwner: boolean, close: () => void, run: Run) => {
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
                modal: <></>,
            }
        },
        run,
    ]
}

function useUpdateDescription(run: Run): MenuItem<Run> {
    const [updateDescriptionOpen, setUpdateDescriptionOpen] = useState(false)
    return [
        (props: ActionMenuProps, isOwner: boolean, close: () => void, run: Run) => {
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

export function Menu(run: Run) {
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
    const restore = useRestore(run)
    const menuItems: MenuItem<any>[] = [shareLink, changeAccess]
    menuItems.push(run.trashed ? restore : del)

    const isTester = useTester(run.owner)
    const updateDescription = useUpdateDescription(run)
    if (isTester) {
        menuItems.push(updateDescription)
    }

    return (
        <ActionMenu id={run.id} description={"run " + run.id} owner={run.owner} access={run.access} items={menuItems} />
    )
}

type UpdateDescriptionModalProps = {
    isOpen: boolean
    onClose(): void
    run: Run
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
                    dispatch(updateDescription(run.id, run.testid, value))
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

export const RunTags = (tags: any) => {
    if (!tags || tags === "") {
        return null
    }
    return Object.entries(tags).map(([key, tag]: any[]) => (
        <Tooltip key={tag} content={key}>
            <Chip key={tag} isReadOnly>
                {typeof tag === "object" ? JSON.stringify(tag) : tag}
            </Chip>
        </Tooltip>
    ))
}
