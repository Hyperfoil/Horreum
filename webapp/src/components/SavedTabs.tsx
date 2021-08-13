import { ReactElement, ReactNode, useState } from 'react'
import { useHistory } from "react-router"
import { Location } from 'history'
import {
    ActionGroup,
    Button,
    Spinner,
    Tab,
    Tabs,
} from '@patternfly/react-core'
import SaveChangesModal from './SaveChangesModal'

type SavedTabProps = {
    title: string,
    fragment: string,
    onSave(): Promise<any>,
    onReset(): void,
    isModified(): boolean,
    children: ReactNode,
}

export const SavedTab: React.FunctionComponent<SavedTabProps> = (_props: SavedTabProps) => null;

type SavedTabsProps = {
    afterSave?(): Promise<any> | void,
    afterReset?(): void,
    children: ReactElement<SavedTabProps> | ReactElement<SavedTabProps>[]
}

export default function SavedTabs(props: SavedTabsProps) {
    const history = useHistory()
    const children = Array.isArray(props.children) ? props.children : [ props.children ]
    const [activeKey, setActiveKey] = useState(() => {
        const index = children.findIndex(c => history.location.hash === "#" + c.props.fragment)
        return index < 0 ? 0 : index
    })
    const [requestedKey, setRequestedKey] = useState<number>()
    const [saving, setSaving] = useState(false)
    const goToTab = (index: number) => {
        setActiveKey(index)
        history.replace(history.location.pathname + "#" + children[index].props.fragment)
    }
    return (<>
        <SaveChangesModal
            isOpen={!!requestedKey}
            onClose={ () => setRequestedKey(undefined) }
            onSave={ () => children[activeKey].props.onSave().then(_ => {
                goToTab(requestedKey as number)
                setRequestedKey(undefined)
                if (props.afterSave) {
                    return props.afterSave()
                }
            }) }
            onReset={() => {
                children[activeKey].props.onReset()
                goToTab(requestedKey as number)
                setRequestedKey(undefined)
                if (props.afterReset) {
                    props.afterReset()
                }
            }}
        />
        <Tabs
            activeKey={ activeKey}
            onSelect={ (_, key) => {
                if (children[activeKey].props.isModified()) {
                    setRequestedKey(key as number)
                } else {
                    goToTab(key as number)
                }
            }}
        >
            { children.map((c, i) => <Tab key={i} eventKey={i} title={c.props.title}>{ c.props.children }</Tab>)}
        </Tabs>
        <ActionGroup style={{ marginTop: 0 }}>
            <Button
                variant="primary"
                isDisabled={saving}
                onClick={ () => {
                    setSaving(true)
                    children[activeKey].props.onSave().then(() => {
                       if (props.afterSave) {
                           return props.afterSave()
                       }
                    }).finally(() => {
                        setSaving(false)
                    })
                }}
            >{ saving ? <>{"Saving... "}<Spinner size="md"/></> : "Save" }</Button>
        </ActionGroup>
    </>)
}