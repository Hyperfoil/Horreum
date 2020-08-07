import React from 'react';
import { Component } from 'react'
import '@patternfly/patternfly/patternfly.css'; //have to use this import to customize scss-variables.scss

import {
  Nav,
  NavItem,
  NavList,
  Page,
  PageHeader,
  PageHeaderTools,
} from '@patternfly/react-core';
import { ConnectedRouter } from 'connected-react-router'
import { NavLink } from 'react-router-dom';

import { Provider, useSelector } from 'react-redux'
import { Route, Switch } from 'react-router'

import store, { history } from './store'
import {
   initKeycloak,
   isAdminSelector,
   LoginLogout,
} from './auth'
import {
  UserProfileLink,
  UserSettings
} from './usersettings'

import AllRuns from './domain/runs/AllRuns';
import TestRuns from './domain/runs/TestRuns';
import Run from './domain/runs/Run';
import AllTests from './domain/tests/AllTests';
import Test from './domain/tests/Test';

import AllSchema from './domain/schemas/AllSchema';
import Schema from './domain/schemas/Schema';

import AllHooks from './domain/hooks/AllHooks';
import Alerts from './alerts'

import Series from './domain/alerting/Series';

class App extends Component {
  constructor(props: any) {
     super(props)
     initKeycloak(store.getState())
  }

  render() {
     return (
       <Provider store={store}>
         <Main />
       </Provider>
     );
  }
}

function Main() {
   const isAdmin = useSelector(isAdminSelector)
   return (
      <ConnectedRouter history={history}>
        <Page header={(
          <PageHeader
            topNav={(
              <Nav aria-label="Nav" variant="horizontal">
                <NavList>
                  { /* TODO: fix links colors properly */ }
                  <NavItem itemId={0}>
                    <NavLink to="/test" style={{ color: "var(--pf-c-nav--m-horizontal__link--Color)"}}>
                      Tests
                    </NavLink>
                  </NavItem>
                  <NavItem itemId={1}>
                    <NavLink to="/run" style={{ color: "var(--pf-c-nav--m-horizontal__link--Color)"}}>
                      Runs
                    </NavLink>
                  </NavItem>
                  { isAdmin &&
                  <NavItem itemId={2}>
                    <NavLink to="/hook" style={{ color: "var(--pf-c-nav--m-horizontal__link--Color)"}}>
                      WebHooks
                    </NavLink>
                  </NavItem>
                  }
                  <NavItem itemId={3}>
                    <NavLink to="/schema" style={{ color: "var(--pf-c-nav--m-horizontal__link--Color)"}}>
                      Schema
                    </NavLink>
                  </NavItem>

                  <NavItem itemId={4}>
                    <NavLink to="/series" style={{ color: "var(--pf-c-nav--m-horizontal__link--Color)"}}>
                      Series
                    </NavLink>
                  </NavItem>
                </NavList>
              </Nav>
            )}
            headerTools={(
               <PageHeaderTools>
                 <UserProfileLink />
                 <LoginLogout />
               </PageHeaderTools>
            )}
          />
        )}
        >
          <Alerts />
          <Switch>
            <Route exact path="/" component={AllTests} />
            <Route exact path="/test" component={AllTests} />
            <Route exact path="/test/:testId" component={Test} />

            <Route exact path="/run" component={AllRuns} />
            <Route exact path="/run/list/:testId" component={TestRuns} />
            <Route exact path="/run/:id" component={Run} />

            <Route exact path="/hook" component={AllHooks} />

            <Route exact path="/schema" component={AllSchema} />
            <Route path="/schema/:schemaId" component={Schema} />

            <Route exact path="/series" component={Series} />

            <Route exact path="/usersettings" component={UserSettings} />

          </Switch>
        </Page>
      </ConnectedRouter>
   );
}

export default App;
