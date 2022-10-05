import { useRef } from "react"
import { Card, CardBody, PageSection } from "@patternfly/react-core"

import SavedTabs, { SavedTab, TabFunctions, saveFunc, resetFunc, modifiedFunc } from "../../components/SavedTabs"
import { FragmentTab } from "../../components/FragmentTabs"
import AllowedSiteList from "../actions/AllowedSiteList"
import ActionList from "../actions/ActionList"
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
                        <FragmentTab title="Global Actions" fragment="actions">
                            <AllowedSiteList />
                            <br />
                            <ActionList />
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
