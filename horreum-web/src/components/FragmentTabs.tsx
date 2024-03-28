import { ReactElement, ReactNode, useMemo, useState, MutableRefObject } from "react"
import { Tab, Tabs } from "@patternfly/react-core"
import { noop } from "../utils"
import {useLocation, useNavigate} from "react-router-dom";

export type FragmentTabProps = {
    title: string
    fragment: string
    isHidden?: boolean
    children: ReactNode
}

export const FragmentTab: React.FunctionComponent<FragmentTabProps> = (_props: FragmentTabProps) => null

type FragmentTabsProps = {
    isVerticle?: boolean
    children: ReactElement<FragmentTabProps> | ReactElement<FragmentTabProps>[]
    tabIndexRef?: MutableRefObject<number>
    navigate?(current: number, next: number): Promise<void>
}

export default function FragmentTabs(props: FragmentTabsProps) {
    const navigate = useNavigate()
    const location = useLocation()
    const children = useMemo(
        () => (Array.isArray(props.children) ? props.children : [props.children]),
        [props.children]
    )
    const [activeKey, setActiveKey] = useState(() => {
        const hash = location.hash
        let endOfTab = hash.length
        for (const symbol of ["+", "&"]) {
            const pos = hash.indexOf(symbol)
            if (pos >= 0 && pos < endOfTab) {
                endOfTab = pos
            }
        }
        const fragment = hash.substring(1, endOfTab)
        const index = Math.max(
            0,
            children.findIndex(c => fragment === c.props.fragment)
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
        navigate(location.pathname + location.search + "#" + children[index].props.fragment, {replace: true})
    }
    return (
        <Tabs
            activeKey={activeKey}
            mountOnEnter={true}
            isVertical={props.isVerticle}
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
