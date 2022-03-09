import { ReactElement, ReactNode, useMemo, useState, MutableRefObject } from "react"
import { useHistory } from "react-router"
import { Tab, Tabs } from "@patternfly/react-core"
import { noop } from "../utils"

type FragmentTabProps = {
    title: string
    fragment: string
    isHidden?: boolean
    children: ReactNode
}

export const FragmentTab: React.FunctionComponent<FragmentTabProps> = (_props: FragmentTabProps) => null

type FragmentTabsProps = {
    children: ReactElement<FragmentTabProps> | ReactElement<FragmentTabProps>[]
    tabIndexRef?: MutableRefObject<number>
    navigate?(current: number, next: number): Promise<void>
}

export default function FragmentTabs(props: FragmentTabsProps) {
    const history = useHistory()
    const children = useMemo(
        () => (Array.isArray(props.children) ? props.children : [props.children]),
        [props.children]
    )
    const [activeKey, setActiveKey] = useState(() => {
        const endOfTab = history.location.hash.indexOf("+")
        const hash = history.location.hash.substring(1, endOfTab >= 0 ? endOfTab : undefined)
        const index = Math.max(
            0,
            children.findIndex(c => hash === c.props.fragment)
        )
        if (props.tabIndexRef) {
            props.tabIndexRef.current = index
        }
        return index
    })
    const goToTab = (index: number) => {
        setActiveKey(index)
        if (props.tabIndexRef) {
            props.tabIndexRef.current = index
        }
        history.replace(history.location.pathname + "#" + children[index].props.fragment)
    }
    return (
        <Tabs
            activeKey={activeKey}
            mountOnEnter={true}
            onSelect={(_, key) => {
                const nKey = key as number
                if (props.navigate) {
                    props
                        .navigate(activeKey, nKey)
                        .then(() => goToTab(nKey))
                        .catch(noop)
                } else {
                    goToTab(nKey)
                }
            }}
        >
            {children.map((c, i) => (
                <Tab
                    key={i}
                    eventKey={i}
                    title={c.props.title}
                    style={{ display: c.props.isHidden ? "none" : "block" }}
                >
                    {c.props.children}
                </Tab>
            ))}
        </Tabs>
    )
}
