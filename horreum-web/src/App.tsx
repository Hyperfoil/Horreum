import React, {useState} from "react"

import "@patternfly/patternfly/patternfly.css" //have to use this import to customize scss-variables.scss
import {BarsIcon} from '@patternfly/react-icons';

import {
    Brand,
    Button,
    Masthead, MastheadBrand, MastheadContent, MastheadLogo, MastheadMain, MastheadToggle,
    Nav,
    NavItem,
    NavList,
    Page, PageSidebar, PageSidebarBody, SkipToContent, Toolbar, ToolbarContent, ToolbarGroup, ToolbarItem
} from '@patternfly/react-core';

import {
    createBrowserRouter,
    createRoutesFromElements,
    NavLink, Outlet,
    Route,
    RouterProvider,
} from "react-router-dom"

import {Provider, useSelector} from "react-redux"
import {isAdminSelector, isManagerSelector, LoginLogout} from "./auth"
import {initKeycloak} from "./keycloak"
import {UserProfileLink, UserSettings} from "./domain/user/UserSettings"

import store from "./store"

import Run from "./domain/runs/Run"
import AllTests from "./domain/tests/AllTests"
import Test from "./domain/tests/Test"
import DatasetComparison from "./domain/runs/DatasetComparison"

import SchemaList from "./domain/schemas/SchemaList"
import Schema from "./domain/schemas/Schema"

import Admin from "./domain/admin/Admin"
import Alerts from "./alerts"

import Banner from "./Banner"
import NotFound from "./404"

import About from "./About"
import ContextProvider from "./context/appContext";
import TableReportConfigPage from "./domain/reports/TableReportConfigPage";
import TableReportPage from "./domain/reports/TableReportPage";

const router = createBrowserRouter(
    createRoutesFromElements(
        <Route element={<Main/>} errorElement={<NotFound/>}>
            <Route index element={<AllTests/>}/>
            <Route path="/test" element={<AllTests/>}/>
            <Route path="/test/:testId" element={<Test/>}/>
            <Route path="/test/:testId/reports/table/config/:configId" element={<TableReportConfigPage/>}/>
            <Route path="/test/:testId/reports/table/:id" element={<TableReportPage/>}/>

            <Route path="/run/:id" element={<Run/>}/>
            <Route path="/dataset/comparison" element={<DatasetComparison/>}/>

            <Route path="/schema" element={<SchemaList/>}/>
            <Route path="/schema/:schemaId" element={<Schema/>}/>

            <Route path="/admin" element={<Admin/>}/>
            <Route path="/usersettings" element={<UserSettings/>}/>
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
    /*
        const isManager = useSelector(isManagerSelector)
        const isAuthenticated = useSelector(isAuthenticatedSelector)
        const [rolesFilter, setRolesFilter] = useState<Team>(ONLY_MY_OWN)

        const [activeGroup, setActiveGroup] = useState('');
        const [activeItem, setActiveItem] = useState('ungrouped_item-1');

        const onSelect = (result: { itemId: number | string; groupId: number | string | null }) => {
            setActiveGroup(result.groupId as string);
            setActiveItem(result.itemId as string);
        };

        const profile = useSelector(userProfileSelector)
        // const watchingTests = useSelector(selectors.watching)
        const navWatching: any[] = []
    */
    /*
        watchingTests.forEach((watching, id) => {
            const test = selectors.get(id)
            watching?.forEach((value, index) => {
                if ( value === profile?.username) {
                    navWatching.push(<NavItem id="test-62">
                        <NavLink
                            to="/test/"
                            style={{color: "var(--pf-c-nav--m-horizontal__link--Color)"}}
                        >
                            {test}
                        </NavLink>
                    </NavItem>)
                }
            })

        })
    */

    const [sidebarOpen, setSidebarOpen] = useState(true);

    const headerToolbar = (
        <Toolbar id="header-toolbar">
            <ToolbarContent>
                <ToolbarGroup align={{ default: 'alignEnd' }}>
                    <ToolbarItem>
                        <UserProfileLink/>
                    </ToolbarItem>
                    {/* { isAdmin && (
                        <ToolbarItem>
                            <AdminLink />
                        </ToolbarItem>
                    )} */}
                    <ToolbarItem>
                        <LoginLogout/>
                    </ToolbarItem>
                    <ToolbarItem>
                        <About/>
                    </ToolbarItem>
                </ToolbarGroup>
            </ToolbarContent>
        </Toolbar>
    )

    const Header = (
        <Masthead>
            <MastheadMain>
                <MastheadToggle>
                    <Button icon={<BarsIcon/>} variant="plain" onClick={() => setSidebarOpen(!sidebarOpen)}
                            aria-label="Global navigation"/>
                </MastheadToggle>
                <MastheadBrand>
                    <MastheadLogo>
                        <Brand src={"/logo.png"} alt="Horreum Logo" heights={{default: '36px'}}/>
                    </MastheadLogo>
                </MastheadBrand>
            </MastheadMain>
            <MastheadContent>
                {headerToolbar}
            </MastheadContent>
        </Masthead>
    );

    const pageId = 'primary-app-container';


    const pageSkipToContent = (
        <SkipToContent onClick={(event) => {
            event.preventDefault();
            const primaryContentContainer = document.getElementById(pageId);
            primaryContentContainer && primaryContentContainer.focus();
        }} href={`#${pageId}`}>
            Skip to Content
        </SkipToContent>
    );


    const navigation = (
        <Nav id="nav-primary-simple">
            <NavList>
                <NavItem itemId={0}>
                    <NavLink to="/test">
                        Tests
                    </NavLink>
                </NavItem>
                <NavItem itemId={1}>
                    <NavLink to="/schema">
                        Schemas
                    </NavLink>
                </NavItem>
                {(isAdmin || isManager ) && (
                    <NavItem itemId={4}>
                        <NavLink to="/admin">
                            Administration
                        </NavLink>
                    </NavItem>
                )}
            </NavList>
        </Nav>

    );

    const sidebar = (
        <PageSidebar>
            <PageSidebarBody>
                {navigation}
            </PageSidebarBody>
        </PageSidebar>
    );

    return (
        <div>
            <Banner/>
            <Page
                mainContainerId={pageId}
                masthead={Header}
                sidebar={sidebarOpen && sidebar}
                skipToContent={pageSkipToContent}>

                <Alerts/>
                <Outlet/>
            </Page>
        </div>
    )
}
