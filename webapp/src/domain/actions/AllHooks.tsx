import React from "react"

import { PageSection } from "@patternfly/react-core"

import PrefixList from "./PrefixList"
import ActionList from "./ActionList"

export default function AllActions() {
    document.title = "Actions | Horreum"

    return (
        <PageSection>
            <PrefixList />
            <br />
            <ActionList />
        </PageSection>
    )
}
