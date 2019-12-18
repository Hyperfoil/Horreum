import React from 'react';
import '@patternfly/patternfly/patternfly.css'; //have to use this import to customize scss-variables.scss

import {
  Page,
  Nav,
  PageHeader,
  PageSidebar,
  NavItem,
  NavList,
  NavVariants,
} from '@patternfly/react-core';
import { ConnectedRouter } from 'connected-react-router'
import { NavLink } from 'react-router-dom';

import { Provider } from 'react-redux'
import { Route, Switch } from 'react-router'

import store, { history } from './store'

import AllRuns from './domain/runs/AllRuns';
import TestRuns from './domain/runs/TestRuns';
import Run from './domain/runs/Run';
import AllTests from './domain/tests/AllTests';
import Test from './domain/tests/Test';

import AllSchema from './domain/schemas/AllSchema';

import AllHooks from './domain/hooks/AllHooks';

function App() {
  return (
    <Provider store={store}>
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
                  <NavItem itemId={0} isActive={false}>
                    <NavLink to="/hook" activeClassName="pf-m-current">
                      WebHooks
                </NavLink>
                  </NavItem>
                  <NavItem itemId={0} isActive={false}>
                    <NavLink to="/schema" activeClassName="pf-m-current">
                      Schema
                </NavLink>
                  </NavItem>
                </NavList>
              </Nav>
            )}
          />
        )}
        // isManagedSidebar={true}
        // sidebar={(
        //   <PageSidebar theme="dark" nav={(
        //     <Nav theme="dark" onSelect={(e) => { console.log("sideNavSelect", e); }} aria-label="Nav">
        //       <NavList variant={NavVariants.simple}>
        //         <NavItem itemId={0} isActive={true}>
        //           <NavLink to="/test" activeClassName="pf-m-current">
        //             Tests
        //     </NavLink>
        //         </NavItem>
        //         <NavItem itemId={0} isActive={false}>
        //           <NavLink to="/run" activeClassName="pf-m-current">
        //             Runs
        //     </NavLink>
        //         </NavItem>
        //         <NavItem itemId={0} isActive={false}>
        //           <NavLink to="/hook" activeClassName="pf-m-current">
        //             WebHooks
        //     </NavLink>
        //         </NavItem>
        //         <NavItem itemId={0} isActive={false}>
        //           <NavLink to="/schema" activeClassName="pf-m-current">
        //             Schema
        //     </NavLink>
        //         </NavItem>
        //       </NavList>
        //     </Nav>
        //   )} />

        // )}

        >
          <Switch>
            <Route exact path="/" component={AllTests} />
            <Route exact path="/test" component={AllTests} />
            <Route exact path="/test/~new" render={() => {
              return (
                <div>So you want a new test, eh?</div>
              )
            }} />
            <Route exact path="/test/:testId" component={Test} />

            <Route exact path="/run" component={AllRuns} />
            <Route exact path="/run/list/:testId" component={TestRuns} />
            <Route exact path="/run/:id" component={Run} />

            <Route exact path="/hook" component={AllHooks} />

            <Route exact path="/schema" component={AllSchema} />
          </Switch>
        </Page>
      </ConnectedRouter>
    </Provider>
  );
}

export default App;
