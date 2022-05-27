import { Card, CardBody, PageSection } from "@patternfly/react-core"

import FragmentTabs, { FragmentTab } from "../../components/FragmentTabs"
import PrefixList from "../hooks/PrefixList"
import HookList from "../hooks/HookList"
import BannerConfig from "./BannerConfig"
import Notifications from "./Notifications"

export default function Admin() {
    return (
        <PageSection>
            <Card>
                <CardBody>
                    <FragmentTabs>
                        <FragmentTab title="Global Webhooks" fragment="hooks">
                            <PrefixList />
                            <br />
                            <HookList />
                        </FragmentTab>
                        <FragmentTab title="Banner" fragment="banner">
                            <BannerConfig />
                        </FragmentTab>
                        <FragmentTab title="Notifications" fragment="notifications">
                            <Notifications />
                        </FragmentTab>
                    </FragmentTabs>
                </CardBody>
            </Card>
        </PageSection>
    )
}
