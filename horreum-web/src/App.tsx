import "@patternfly/patternfly/patternfly.css" //have to use this import to customize scss-variables.scss

import {Nav, NavItem, NavList, Page, PageHeader, PageHeaderTools} from "@patternfly/react-core"

import {Router, NavLink} from "react-router-dom"

import {Provider, useSelector} from "react-redux"
import {Route, Switch} from "react-router"

import store from "./store"
import {isAdminSelector, LoginLogout} from "./auth"
import {initKeycloak} from "./keycloak"
import {UserProfileLink, UserSettings} from "./domain/user/UserSettings"

import RunList from "./domain/runs/RunList"
import TestDatasets from "./domain/runs/TestDatasets"
import Run from "./domain/runs/Run"
import AllTests from "./domain/tests/AllTests"
import Test from "./domain/tests/Test"
import DatasetComparison from "./domain/runs/DatasetComparison"

import SchemaList from "./domain/schemas/SchemaList"
import Schema from "./domain/schemas/Schema"

import Admin from "./domain/admin/Admin"
import Alerts from "./alerts"

import Changes from "./domain/alerting/Changes"

import Reports from "./domain/reports/Reports"
import Banner from "./Banner"
import TableReportPage from "./domain/reports/TableReportPage"
import TableReportConfigPage from "./domain/reports/TableReportConfigPage"
import NotFound from "./404"

import About from "./About"
import ContextProvider, {history} from "./context/appContext";

export default function App() {
    initKeycloak(store.getState())

    return (
        <Provider store={store}>
            <Main/>
        </Provider>
    )
}

function Main() {
    const isAdmin = useSelector(isAdminSelector)
    return (
        <Provider store={store}>
            <ContextProvider>
                <Router history={history}>
                    <Banner />
                    <Page
                        header={
                            <PageHeader
                                topNav={
                                    <Nav aria-label="Nav" variant="horizontal">
                                        <NavList>
                                            <NavItem itemId={-1}>
                                                <NavLink to="/">
                                                    <img width="24" height="24" src="/logo.png" alt="Horreum Logo" />
                                                </NavLink>
                                            </NavItem>
                                            <NavItem itemId={0}>
                                                <NavLink to="/test">Tests</NavLink>
                                            </NavItem>
                                            <NavItem itemId={1}>
                                                <NavLink to="/schema">Schema</NavLink>
                                            </NavItem>
                                            <NavItem itemId={2}>
                                                <NavLink to="/changes">Changes</NavLink>
                                            </NavItem>
                                            <NavItem itemId={3}>
                                                <NavLink to="/reports">Reports</NavLink>
                                            </NavItem>
                                            {isAdmin && (
                                                <NavItem itemId={4}>
                                                    <NavLink to="/admin">Administration</NavLink>
                                                </NavItem>
                                            )}
                                        </NavList>
                                    </Nav>
                                }
                        headerTools={
                            <PageHeaderTools>
                                <UserProfileLink />
                                <LoginLogout />
                                <About />
                            </PageHeaderTools>
                        }
                    />
                }
            >
                <Alerts/>
                <Switch>
                    <Route exact path="/" component={AllTests} />
                    <Route exact path="/test" component={AllTests} />
                    <Route exact path="/test/:testId" component={Test} />

                    <Route exact path="/run/list/:testId" component={RunList} />
                    <Route exact path="/run/dataset/list/:testId" component={TestDatasets} />
                    <Route exact path="/run/:id" component={Run} />
                    <Route exact path="/dataset/comparison" component={DatasetComparison} />

                    <Route exact path="/schema" component={SchemaList} />
                    <Route path="/schema/:schemaId" component={Schema} />

                    <Route exact path="/changes" component={Changes} />

                    <Route exact path="/reports" component={Reports} />
                    <Route exact path="/reports/table/config/:configId" component={TableReportConfigPage} />
                    <Route exact path="/reports/table/:id" component={TableReportPage} />

                    <Route exact path="/admin" component={Admin} />
                    <Route exact path="/usersettings" component={UserSettings} />
                    <Route component={NotFound} />
                </Switch>
            </Page>
            {/* <ContextHelp /> */}
        </Router>
        </ContextProvider>
        </Provider>
    )
}
