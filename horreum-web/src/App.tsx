import React, {useState} from "react"

import "@patternfly/patternfly/patternfly.css" //have to use this import to customize scss-variables.scss
import {BarsIcon} from '@patternfly/react-icons';

import {
    Brand,
    Button,
    Masthead, MastheadBrand, MastheadContent, MastheadMain, MastheadToggle,
    Nav,
    NavItem,
    NavList,
    Page, PageSidebar, PageSidebarBody, Sidebar, SidebarContent, SidebarPanel, SkipToContent
} from '@patternfly/react-core';

import {
    createBrowserRouter,
    createRoutesFromElements,
    NavLink, Outlet,
    Route,
    RouterProvider,
} from "react-router-dom"

import {Provider, useSelector} from "react-redux"
import {isAdminSelector, isAuthenticatedSelector, isManagerSelector, LoginLogout, userProfileSelector} from "./auth"
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
import {ONLY_MY_OWN, Team} from "./components/TeamSelect";
import {PageHeader, PageHeaderTools} from "@patternfly/react-core/deprecated";

const router = createBrowserRouter(
    createRoutesFromElements(
        <Route element={<Main/>}>
            <Route index element={<AllTests/>}/>
            <Route path="/test" element={<AllTests/>}/>
            <Route path="/test/:testId" element={<Test/>}/>

            {/*<Route path="/run/list/:testId" element={<RunList/>}/>*/}
            {/*<Route path="/run/dataset/list/:testId" element={<TestDatasets/>}/>*/}
            <Route path="/run/:id" element={<Run/>}/>
            <Route path="/dataset/comparison" element={<DatasetComparison/>}/>

            <Route path="/schema" element={<SchemaList/>}/>
            <Route path="/schema/:schemaId" element={<Schema/>}/>

            {/*<Route path="/changes" element={<Changes testID={1}/>}/>*/}

            {/*<Route path="/reports" element={<Reports  testId={1}/>}/>*/}
            {/*<Route path="/reports/table/config/:configId" element={<TableReportConfigPage/>}/>*/}
            {/*<Route path="/reports/table/:id" element={<TableReportPage/>}/>*/}

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

    const Header = (
        <Masthead>
            <MastheadToggle>
                <Button variant="plain" onClick={() => setSidebarOpen(!sidebarOpen)} aria-label="Global navigation">
                    <BarsIcon/>
                </Button>
            </MastheadToggle>
            <MastheadMain>
                <MastheadBrand>
                    {/*<Brand src={logo} alt="Patterfly Logo" heights={{ default: '36px' }} />*/}
                    {/*<Brand alt="Horreum" heights={{default: '36px'}}/>*/}
                </MastheadBrand>
            </MastheadMain>
            <MastheadContent>
                <UserProfileLink/>
                {/*{isAdmin && (*/}
                {/*    <AdminLink />*/}
                {/*)}*/}
                <LoginLogout/>
                <About/>

            </MastheadContent>

            {/*<PageHeaderTools>*/}

            {/*</PageHeaderTools>*/}

        </Masthead>
    );

    const pageId = 'primary-app-container';


    const PageSkipToContent = (
        <SkipToContent onClick={(event) => {
            event.preventDefault();
            const primaryContentContainer = document.getElementById(pageId);
            primaryContentContainer && primaryContentContainer.focus();
        }} href={`#${pageId}`}>
            Skip to Content
        </SkipToContent>
    );


    const Navigation = (
        <Nav id="nav-primary-simple" theme="dark">
            <NavList>
                <NavItem itemId={0}>
                    <NavLink
                        to="/test"
                        style={{color: "var(--pf-c-nav--m-horizontal__link--Color)"}}
                    >
                        Tests
                    </NavLink>
                </NavItem>
                <NavItem itemId={1}>
                    <NavLink
                        to="/schema"
                        style={{color: "var(--pf-c-nav--m-horizontal__link--Color)"}}
                    >
                        Schemas
                    </NavLink>
                </NavItem>
                {isAdmin && (
                    <NavItem itemId={4}>
                        <NavLink
                            to="/admin"
                            style={{color: "var(--pf-c-nav--m-horizontal__link--Color)"}}
                        >
                            Administration
                        </NavLink>
                    </NavItem>
                )}
            </NavList>
        </Nav>

    );

    const Sidebar = (
        <PageSidebar theme="dark">
            <PageSidebarBody>
                {Navigation}
            </PageSidebarBody>
        </PageSidebar>
    );

    return (
        <div>
            <Banner/>
            <Page
                mainContainerId={pageId}
                header={Header}
                sidebar={sidebarOpen && Sidebar}
                skipToContent={PageSkipToContent}>

                <Alerts/>
                <Outlet/>
            </Page>
        </div>
    )
}
