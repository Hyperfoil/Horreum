import {useContext, useState} from "react"
import { Button, DropdownItem, Modal, Spinner, TextInput, Tooltip } from "@patternfly/react-core"
import { WarningTriangleIcon } from "@patternfly/react-icons"
import moment from "moment"
import ActionMenu, {
    ActionMenuProps,
    MenuItem,
    useChangeAccess,
    useDelete,
} from "../../components/ActionMenu"
import { formatDateTime, toEpochMillis } from "../../utils"
import { useTester } from "../../auth"
import {Access, recalculateDatasets, RunSummary, trash, updateDescription, updateRunAccess} from "../../api"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";

export function Description(description: string) {
    const truncated = (
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
    )
    return (
        <Tooltip content={description}>
            {typeof description === "string" &&
            (description.startsWith("http://") || description.startsWith("https://")) ? (
                <a href={description} target="_blank">
                    {truncated}
                </a>
            ) : (
                truncated
            )}
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
    const { alerting } = useContext(AppContext) as AppContextType;
    return [
        (props: ActionMenuProps, isTester: boolean, close: () => void, run: RunSummary) => {
            return {
                item: (
                    <DropdownItem
                        key="restore"
                        onClick={() => {
                            close()
                            trash(alerting, run.id, run.testid, false).then()
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
    const { alerting } = useContext(AppContext) as AppContextType;
    const [recalculating, setRecalculating] = useState(false)
    return [
        (props: ActionMenuProps, isTester: boolean, close: () => void) => {
            return {
                item: (
                    <DropdownItem
                        key="recalculate"
                        isDisabled={!isTester || recalculating}
                        onClick={() => {
                            setRecalculating(true)
                            recalculateDatasets(props.id, run.testid, alerting)
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
        (props: ActionMenuProps, isTester: boolean, close: () => void, run: RunSummary) => {
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

export function Menu(run: RunSummary, refreshCallback: () => void) {
    const { alerting } = useContext(AppContext) as AppContextType;

    const changeAccess = useChangeAccess({
        onAccessUpdate: (id, owner, access) => updateRunAccess(id, run.testid, owner, access, alerting).then(refreshCallback),
    })
    const del = useDelete({
        onDelete: id => trash(alerting, id, run.testid).then(refreshCallback),
    })
    const recalculate = useRecalculateDatasets(run)
    const restore = useRestore(run)
    const menuItems: MenuItem<any>[] = [changeAccess, recalculate]
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
    const { alerting } = useContext(AppContext) as AppContextType;
    const [value, setValue] = useState(run.description)
    const [updating, setUpdating] = useState(false)

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
                    updateDescription(run.id, run.testid, value || "", alerting)
                        .then(_ => setValue(run.description))
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

export function renderCell(renderString: string | undefined, sub: string | undefined, token: string | undefined) {
    const render = renderString ? new Function("return " + renderString)() : undefined
    return (arg: any) => {
        const {
            cell: {
                value: cellValue,
                row: { index },
            },
            data,
        } = arg
        return renderImpl(cellValue, render, sub, data[index], token)
    }
}

export function renderValue(renderString: string | undefined, sub: string | undefined, token: string | undefined) {
    const render = renderString ? new Function("return " + renderString)() : undefined
    return (value: any, fullItem: any) => renderImpl(value, render, sub, fullItem, token)
}

type RenderFunction = (value: any, fullItem: any, token?: string) => any

function renderImpl(value: any, render: RenderFunction, sub?: string, fullItem?: any, token?: string) {
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
    try {
        const rendered = render(value, fullItem, token)
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
        console.warn("Error in render function %s trying to render %O: %O", render.toString(), value, e)
        return (
            <Tooltip
                content={
                    <span>
                        Error in render function{" "}
                        <pre>
                            <code>{render}</code>
                        </pre>
                        trying to render{" "}
                        <pre>
                            <code>{JSON.stringify(value)}</code>
                        </pre>
                        : {e}
                    </span>
                }
            >
                <WarningTriangleIcon style={{ color: "#a30000" }} />
            </Tooltip>
        )
    }
}
