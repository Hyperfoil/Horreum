import "@patternfly/patternfly/patternfly.css" //have to use this import to customize scss-variables.scss

import {
    Nav,
    NavItem,
    NavList,
    Page
} from '@patternfly/react-core';
import {
    PageHeader,
    PageHeaderTools
} from '@patternfly/react-core/deprecated';

import {
    createBrowserRouter,
    createRoutesFromElements,
    NavLink, Outlet,
    Route,
    RouterProvider,
} from "react-router-dom"

import {Provider, useSelector} from "react-redux"

import store from "./store"
import {isAdminSelector, isManagerSelector, LoginLogout} from "./auth"
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
import ContextProvider from "./context/appContext";

const router = createBrowserRouter(
    createRoutesFromElements(
        <Route element={<Main/>} >
            <Route index element={<AllTests/>}/>
            <Route path="/test" element={<AllTests/>}/>
            <Route path="/test/:testId" element={<Test/>}/>

            <Route path="/run/list/:testId" element={<RunList/>}/>
            <Route path="/run/dataset/list/:testId" element={<TestDatasets/>}/>
            <Route path="/run/:id" element={<Run/>}/>
            <Route path="/dataset/comparison" element={<DatasetComparison/>}/>

            <Route path="/schema" element={<SchemaList/>}/>
            <Route path="/schema/:schemaId" element={<Schema/>}/>

            <Route path="/changes" element={<Changes/>}/>

            <Route path="/reports" element={<Reports/>}/>
            <Route path="/reports/table/config/:configId" element={<TableReportConfigPage/>}/>
            <Route path="/reports/table/:id" element={<TableReportPage/>}/>

            <Route path="/admin" element={<Admin/>}/>
            <Route path="/usersettings" element={<UserSettings/>}/>
            <Route element={<NotFound/>}/>
        </Route>
    )
);

export default function App() {
    initKeycloak(store.getState())

    return (
        <Provider store={store}>
            <ContextProvider>
                <RouterProvider router={router}/>
            </ContextProvider>
        </Provider>
    )
}

function Main() {
    const isAdmin = useSelector(isAdminSelector)
    const isManager = useSelector(isManagerSelector)


    return (
        <div>
            <Banner/>
            <Page
                header={
                    <PageHeader
                        topNav={
                            <Nav aria-label="Nav" variant="horizontal">
                                <NavList>
                                    <NavItem itemId={-1}>
                                        <NavLink to="/">
                                            <img width="24" height="24" src="/logo.png" alt="Horreum Logo"/>
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
                                    { (isAdmin || isManager) && (
                                        <NavItem itemId={4}>
                                            <NavLink to="/admin">Administration</NavLink>
                                        </NavItem>
                                    )}
                                </NavList>
                            </Nav>
                        }
                        headerTools={
                            <PageHeaderTools>
                                <UserProfileLink/>
                                <LoginLogout/>
                                <About/>
                            </PageHeaderTools>
                        }
                    />
                }>
                <Alerts/>

                <Outlet />
            </Page>
        </div>
    )
}
