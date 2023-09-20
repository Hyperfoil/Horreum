import { Component } from "react"
import "@patternfly/patternfly/patternfly.css" //have to use this import to customize scss-variables.scss

import { Nav, NavItem, NavList, Page, PageHeader, PageHeaderTools } from "@patternfly/react-core"
import { ReduxRouter } from '@lagunovsky/redux-react-router'
import {createBrowserRouter, RouterProvider, Route, Routes, Link } from "react-router-dom"

import { Provider, useSelector } from "react-redux"
import { Switch } from "react-router"

import store, { history } from "./store"
import { isAdminSelector, LoginLogout } from "./auth"
import { initKeycloak } from "./keycloak"
import { UserProfileLink, UserSettings } from "./domain/user/UserSettings"

import TestRuns from "./domain/runs/TestRuns"
import TestDatasets from "./domain/runs/TestDatasets"
import Run from "./domain/runs/Run"
import AllTests from "./domain/tests/AllTests"
import Test from "./domain/tests/Test"
import DatasetComparison from "./domain/runs/DatasetComparison"

import AllSchema from "./domain/schemas/AllSchema"
import Schema from "./domain/schemas/Schema"

import Admin from "./domain/admin/Admin"
import Alerts from "./alerts"

import Changes from "./domain/alerting/Changes"

import Reports from "./domain/reports/Reports"
import Banner from "./Banner"
import TableReportPage from "./domain/reports/TableReportPage"
import TableReportConfigPage from "./domain/reports/TableReportConfigPage"

import About from "./About"

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
        )
    }
}

function Main() {
    const isAdmin = useSelector(isAdminSelector)
    return (
        <ReduxRouter history={history}>
            <Banner />
            <Page
                header={
                    <PageHeader
                        topNav={
                            <Nav aria-label="Nav" variant="horizontal">
                                <NavList>
                                    <NavItem itemId={-1}>
                                        <Link to="/">
                                            <img width="24" height="24" src="/logo.png" alt="Horreum Logo" />
                                        </NavLink>
                                    </NavItem>
                                    {/* TODO: fix links colors properly */}
                                    <NavItem itemId={0}>
                                        <NavLink
                                            to="/test"
                                            style={{ fontWeight: "bold", color: "var(--pf-c-nav--m-horizontal__link--Color)" }}
                                        >
                                            Tests
                                        </NavLink>
                                    </NavItem>
                                    <NavItem itemId={1}>
                                        <NavLink
                                            to="/schema"
                                            style={{ fontWeight: "bold", color: "var(--pf-c-nav--m-horizontal__link--Color)" }}
                                        >
                                            Schema
                                        </NavLink>
                                    </NavItem>
                                    <NavItem itemId={2}>
                                        <NavLink
                                            to="/changes"
                                            style={{ fontWeight: "bold", color: "var(--pf-c-nav--m-horizontal__link--Color)" }}
                                        >
                                            Changes
                                        </NavLink>
                                    </NavItem>
                                    <NavItem itemId={3}>
                                        <NavLink
                                            to="/reports"
                                            style={{ fontWeight: "bold", color: "var(--pf-c-nav--m-horizontal__link--Color)" }}
                                        >
                                            Reports
                                        </NavLink>
                                    </NavItem>
                                    {isAdmin && (
                                        <NavItem itemId={4}>
                                            <NavLink
                                                to="/admin"
                                                style={{ color: "var(--pf-c-nav--m-horizontal__link--Color)" }}
                                            >
                                                Administration
                                            </NavLink>
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
                <Alerts />
                <Routes>
                    <Route exact path="/" elements={AllTests} />
                    <Route exact path="/test" elements={AllTests} />
                    <Route exact path="/test/:testId" elements={Test} />

                    <Route exact path="/run/list/:testId" elements={TestRuns} />
                    <Route exact path="/run/dataset/list/:testId" elements={TestDatasets} />
                    <Route exact path="/run/:id" elements={Run} />
                    <Route exact path="/dataset/comparison" elements={DatasetComparison} />

                    <Route exact path="/schema" elements={AllSchema} />
                    <Route path="/schema/:schemaId" elements={Schema} />

                    <Route exact path="/changes" elements={Changes} />

                    <Route exact path="/reports" elements={Reports} />
                    <Route exact path="/reports/table/config/:configId" elements={TableReportConfigPage} />
                    <Route exact path="/reports/table/:id" elements={TableReportPage} />

                    <Route exact path="/admin" elements={Admin} />
                    <Route exact path="/usersettings" elements={UserSettings} />
                </Routes>
            </Page>
        </ReduxRouter>
    )
}

export default App
