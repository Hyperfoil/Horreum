import { useRef } from "react"
import { Card, CardBody, PageSection } from "@patternfly/react-core"

import SavedTabs, { SavedTab, TabFunctions, saveFunc, resetFunc, modifiedFunc } from "../../components/SavedTabs"
import { FragmentTab } from "../../components/FragmentTabs"
import PrefixList from "../hooks/PrefixList"
import HookList from "../hooks/HookList"
import BannerConfig from "./BannerConfig"
import Notifications from "./Notifications"
import Teams from "./Teams"
import Administrators from "./Administrators"

export default function Admin() {
    const adminFuncsRef = useRef<TabFunctions>()
    const teamsFuncsRef = useRef<TabFunctions>()
    return (
        <PageSection>
            <Card>
                <CardBody>
                    <SavedTabs>
                        <SavedTab
                            title="Administrators"
                            fragment="administrators"
                            onSave={saveFunc(adminFuncsRef)}
                            onReset={resetFunc(adminFuncsRef)}
                            isModified={modifiedFunc(adminFuncsRef)}
                        >
                            <Administrators funcsRef={adminFuncsRef} />
                        </SavedTab>
                        <SavedTab
                            title="Teams"
                            fragment="teams"
                            onSave={saveFunc(teamsFuncsRef)}
                            onReset={resetFunc(teamsFuncsRef)}
                            isModified={modifiedFunc(teamsFuncsRef)}
                        >
                            <Teams funcs={teamsFuncsRef} />
                        </SavedTab>
                        <FragmentTab title="Global Webhooks" fragment="hooks">
                            <PrefixList />
                            <br />
                            <HookList />
                        </FragmentTab>
                        <FragmentTab title="Banner" fragment="banner">
                            <BannerConfig />
                        </FragmentTab>
                        <FragmentTab title="Notification Tests" fragment="notifications">
                            <Notifications />
                        </FragmentTab>
                    </SavedTabs>
                </CardBody>
            </Card>
        </PageSection>
    )
}
