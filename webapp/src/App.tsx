import { Component } from "react"
import "@patternfly/patternfly/patternfly.css" //have to use this import to customize scss-variables.scss

import { Nav, NavItem, NavList, Page, PageHeader, PageHeaderTools } from "@patternfly/react-core"
import { ReduxRouter } from '@lagunovsky/redux-react-router'
import { NavLink } from "react-router-dom"

import { Provider, useSelector } from "react-redux"
import { Route, Routes } from "react-router"

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
                                        <NavLink to="/">
                                            <img width="24" height="24" src="/logo.png" alt="Horreum Logo" />
                                        </NavLink>
                                    </NavItem>
                                    {/* TODO: fix NavLinks colors properly */}
                                    <NavItem itemId={0}>
                                        <NavLink
                                            to="/test"
                                            style={{ fontWeight: "bold", color: "var(--pf-c-nav--m-horizontal__NavLink--Color)" }}
                                        >
                                            Tests
                                        </NavLink>
                                    </NavItem>
                                    <NavItem itemId={1}>
                                        <NavLink
                                            to="/schema"
                                            style={{ fontWeight: "bold", color: "var(--pf-c-nav--m-horizontal__NavLink--Color)" }}
                                        >
                                            Schema
                                        </NavLink>
                                    </NavItem>
                                    <NavItem itemId={2}>
                                        <NavLink
                                            to="/changes"
                                            style={{ fontWeight: "bold", color: "var(--pf-c-nav--m-horizontal__NavLink--Color)" }}
                                        >
                                            Changes
                                        </NavLink>
                                    </NavItem>
                                    <NavItem itemId={3}>
                                        <NavLink
                                            to="/reports"
                                            style={{ fontWeight: "bold", color: "var(--pf-c-nav--m-horizontal__NavLink--Color)" }}
                                        >
                                            Reports
                                        </NavLink>
                                    </NavItem>
                                    {isAdmin && (
                                        <NavItem itemId={4}>
                                            <NavLink
                                                to="/admin"
                                                style={{ color: "var(--pf-c-nav--m-horizontal__NavLink--Color)" }}
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
                    <Route path="/" element={<AllTests />} />
                    <Route path="/test" element={<AllTests />} />
                    <Route path="/test/:testId" element={<Test />} />
                    <Route path="/run/list/:testId" element={<TestRuns />} />
                    <Route path="/run/dataset/list/:testId" element={<TestDatasets />} />
                    <Route path="/run/:id" element={<Run />} />
                    <Route path="/dataset/comparison" element={<DatasetComparison />} />
                    <Route path="/schema" element={<AllSchema />} />
                    <Route path="/schema/:schemaId" element={<Schema />} />
                    <Route path="/changes" element={<Changes />} />
                    <Route path="/reports" element={<Reports />} />
                    <Route path="/reports/table/config/:configId" element={<TableReportConfigPage />} />
                    <Route path="/reports/table/:id" element={<TableReportPage />} />
                    <Route path="/admin" element={<Admin />} />
                    <Route path="/usersettings" element={<UserSettings />} />
                </Routes>
            </Page>
        </ReduxRouter>
    )
}

export default App
