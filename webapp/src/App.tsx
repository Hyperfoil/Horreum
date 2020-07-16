import React from 'react';
import { Component } from 'react'
import '@patternfly/patternfly/patternfly.css'; //have to use this import to customize scss-variables.scss

import {
  Nav,
  NavItem,
  NavList,
  NavVariants,
  Page,
  PageHeader,
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

import AllRuns from './domain/runs/AllRuns';
import TestRuns from './domain/runs/TestRuns';
import Run from './domain/runs/Run';
import AllTests from './domain/tests/AllTests';
import Test from './domain/tests/Test';

import AllSchema from './domain/schemas/AllSchema';
import Schema from './domain/schemas/Schema';

import AllHooks from './domain/hooks/AllHooks';
import Alerts from './alerts'

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
            // showNavToggle={true}
            topNav={(
              <Nav aria-label="Nav">
                <NavList variant={NavVariants.horizontal}>
                  <NavItem itemId={0} isActive={false}>
                    <NavLink to="/test" activeClassName="pf-m-current">
                      Tests
                    </NavLink>
                  </NavItem>
                  <NavItem itemId={0} isActive={false}>
                    <NavLink to="/run" activeClassName="pf-m-current">
                      Runs
                    </NavLink>
                  </NavItem>
                  { isAdmin &&
                  <NavItem itemId={0} isActive={false}>
                    <NavLink to="/hook" activeClassName="pf-m-current">
                      WebHooks
                    </NavLink>
                  </NavItem>
                  }
                  <NavItem itemId={0} isActive={false}>
                    <NavLink to="/schema" activeClassName="pf-m-current">
                      Schema
                    </NavLink>
                  </NavItem>
                </NavList>
              </Nav>
            )}
            toolbar={(
               <LoginLogout />
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

          </Switch>
        </Page>
      </ConnectedRouter>
   );
}

export default App;
