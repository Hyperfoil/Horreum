import { MutableRefObject, ReactElement, useMemo, useState, useRef } from "react"
import { ActionGroup, Button, Spinner } from "@patternfly/react-core"
import SaveChangesModal from "./SaveChangesModal"
import FragmentTabs, { FragmentTab, FragmentTabProps } from "./FragmentTabs"
import { noop } from "../utils"
import {useBlocker, useNavigate} from 'react-router-dom';

export type TabFunctions = {
    save(): Promise<any>
    reset(): void
    modified?(): boolean
}

export type TabFunctionsRef = MutableRefObject<TabFunctions | undefined>

export function saveFunc(ref: TabFunctionsRef) {
    return () => (ref.current ? ref.current?.save() : Promise.resolve())
}
export function resetFunc(ref: TabFunctionsRef) {
    return () => ref.current?.reset()
}
export function modifiedFunc(ref: TabFunctionsRef) {
    return () => ref.current?.modified?.() || false
}

type SavedTabProps = FragmentTabProps & {
    onSave(): Promise<any>
    onReset?(): void
    isModified(): boolean
}

export const SavedTab: React.FunctionComponent<SavedTabProps> = (_props: SavedTabProps) => null

type SavedTabsProps = {
    afterSave?(): Promise<any> | void
    afterReset?(): void
    children: ReactElement<SavedTabProps | FragmentTabProps> | ReactElement<SavedTabProps | FragmentTabProps>[]
    canSave?: boolean
}

export default function SavedTabs(props: SavedTabsProps) {
    const navigate = useNavigate()
    const children = useMemo(
        () => (Array.isArray(props.children) ? props.children : [props.children]),
        [props.children]
    )
    const activeKey = useRef(0)
    const [saving, setSaving] = useState(false)

    let blocker = useBlocker(
        ({ currentLocation, nextLocation }) => {
            const childProps = children[activeKey.current].props;
            return ("isModified" in childProps && childProps.isModified()) &&
            currentLocation.pathname !== nextLocation.pathname
        }
    );

    return (
        <>
            <SaveChangesModal
                isOpen={blocker.state === "blocked"}
                onClose={ () => blocker.reset?.()}
                onSave={() => {
                    const childProps = children[activeKey.current].props
                    if ("onSave" in childProps) {
                        return childProps.onSave().then(_ => {
                            blocker.proceed?.()
                            if (props.afterSave) {
                                return props.afterSave()
                            }
                        })
                    } else {
                        return Promise.reject("Cannot save unsaveable tab!")
                    }
                }}
                onReset={() => {
                    const childProps = children[activeKey.current].props
                    if ("onReset" in childProps) {
                        if( childProps.onReset ) {
                            childProps.onReset()
                        }
                        if (props.afterReset) {
                            props.afterReset()
                        }
                    }
                    blocker.proceed?.()
                }}
            />
            <FragmentTabs tabIndexRef={activeKey} >
                {children.map((c, i) => (
                    <FragmentTab key={i} {...c.props} />
                ))}
            </FragmentTabs>
            {props.canSave !== false && "onSave" in children[activeKey.current].props && (
                <ActionGroup style={{ marginTop: 0 }}>
                    <Button
                        variant="primary"
                        isDisabled={saving}
                        onClick={() => {
                            setSaving(true)
                            const childProps = children[activeKey.current].props
                            if ("onSave" in childProps) {
                                childProps
                                    .onSave?.()
                                    .then(() => {
                                        if (props.afterSave) {
                                            return props.afterSave()
                                        }
                                    }, noop)
                                    .finally(() => {
                                        setSaving(false)
                                    })
                            }
                        }}
                    >
                        {saving ? (
                            <>
                                {"Saving... "}
                                <Spinner size="md" />
                            </>
                        ) : (
                            "Save"
                        )}
                    </Button>
                </ActionGroup>
            )}
        </>
    )
}
