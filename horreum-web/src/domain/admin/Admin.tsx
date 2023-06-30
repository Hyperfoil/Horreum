import {useRef} from "react"
import {Card, CardBody, NavItem, PageSection} from "@patternfly/react-core"

import SavedTabs, {SavedTab, TabFunctions, saveFunc, resetFunc, modifiedFunc} from "../../components/SavedTabs"
import FragmentTabs, {FragmentTab} from "../../components/FragmentTabs"
import AllowedSiteList from "../actions/AllowedSiteList"
import ActionList from "../actions/ActionList"
import BannerConfig from "./BannerConfig"
import Notifications from "./Notifications"
import Teams from "./Teams"
import Administrators from "./Administrators"
import {useSelector} from "react-redux";
import {isAdminSelector, isManagerSelector} from "../../auth";
import Datastores from "./Datastores";

export default function Admin() {
    const adminFuncsRef = useRef<TabFunctions>()
    const teamsFuncsRef = useRef<TabFunctions>()
    const isAdmin = useSelector(isAdminSelector)
    const isManager = useSelector(isManagerSelector)

    if (isAdmin) {
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
                                <Administrators funcsRef={adminFuncsRef}/>
                            </SavedTab>
                            <SavedTab
                                title="Teams"
                                fragment="teams"
                                onSave={saveFunc(teamsFuncsRef)}
                                onReset={resetFunc(teamsFuncsRef)}
                                isModified={modifiedFunc(teamsFuncsRef)}
                            >
                                <Teams funcs={teamsFuncsRef}/>
                            </SavedTab>
                            <FragmentTab title="Global Actions" fragment="actions">
                                <AllowedSiteList/>
                                <br/>
                                <ActionList/>
                            </FragmentTab>
                            <FragmentTab title="Banner" fragment="banner">
                                <BannerConfig/>
                            </FragmentTab>
                            <FragmentTab title="Notification Tests" fragment="notifications">
                                <Notifications/>
                            </FragmentTab>
                            <FragmentTab title="Datastores" fragment="datastores">
                                <Datastores/>
                            </FragmentTab>
                        </SavedTabs>
                    </CardBody>
                </Card>
            </PageSection>
        )
    } else if (isManager) {
        return (
            <PageSection>
                <Card>
                    <CardBody>
                        <FragmentTabs>
                            <FragmentTab title="Datastores" fragment="datastores">
                                <Datastores/>
                            </FragmentTab>
                        </FragmentTabs>
                    </CardBody>
                </Card>
            </PageSection>
        )

    } else {
        return (
            <PageSection>
                <Card>
                    <CardBody>
                        You are not authorized to view this page
                    </CardBody>
                </Card>
            </PageSection>
        )

    }
}
