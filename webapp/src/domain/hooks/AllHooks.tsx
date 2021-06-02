import React from 'react';

import {
    PageSection,
} from '@patternfly/react-core';

import PrefixList from './PrefixList'
import HookList from './HookList'

export default ()=>{
    document.title = "WebHooks | Horreum"

    return (
        <PageSection>
            <PrefixList />
            <br />
            <HookList />
        </PageSection>
    )
}