import React, {useContext, useEffect, useState} from "react"

import "@patternfly/patternfly/patternfly.css" //have to use this import to customize scss-variables.scss
import {BarsIcon} from '@patternfly/react-icons';

import {
    Brand, Bullseye,
    Button,
    Masthead, MastheadBrand, MastheadContent, MastheadLogo, MastheadMain, MastheadToggle,
    Nav,
    NavItem,
    NavList,
    Page, PageSidebar, PageSidebarBody, SkipToContent, Spinner, Toolbar, ToolbarContent, ToolbarGroup, ToolbarItem
} from '@patternfly/react-core';

import {
    createBrowserRouter,
    createRoutesFromElements,
    NavLink, Outlet,
    Route,
    RouterProvider,
} from "react-router-dom"

import {LoginLogout} from "./auth/auth"
import {UserProfileLink, UserSettings} from "./domain/user/UserSettings"

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
import AppContextProvider from "./context/AppContext";
import TableReportConfigPage from "./domain/reports/TableReportConfigPage";
import TableReportPage from "./domain/reports/TableReportPage";
import {createUserManager, onSigninCallback} from "./auth/oidc";
import {AuthProvider} from "react-oidc-context";
import {KeycloakConfig} from "./generated";
import {configApi} from "./api";
import AuthBridgeContextProvider, {AuthBridgeContext} from "./context/AuthBridgeContext";
import {AuthContextType} from "./context/@types/authContextTypes";
import CallbackSSO from "./auth/CallbackSSO";

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

            {/* matches the OIDC redirect_uri configured in oidc.ts */}
            <Route path="/callback-sso" element={<CallbackSSO/>}/>
        </Route>
    ), {
        future: {
            // loaders will no longer revalidate by default after an action throws/returns a Response with a 4xx/5xx status code
            v7_skipActionErrorRevalidation: true,
            // it is also useful if you are using lazy to load your route modules
            v7_partialHydration: true,
            // This normalizes formMethod fields as uppercase HTTP methods to align with the fetch() behavior
            v7_normalizeFormMethod: true,
            // The fetcher lifecycle is now based on when it returns to an idle state rather than when its owner component unmounts
            v7_fetcherPersist: true,
            // This uses React.useTransition instead of React.useState for Router state updates
            v7_startTransition: true,
            // Changes the relative path matching and linking for multi-segment splats paths like dashboard/* (vs. just *)
            v7_relativeSplatPath: true,
        },
    }
);

export default function App() {
    const [horreumOidcConfig, setHorreumOidcConfig] = useState<KeycloakConfig | undefined>()

    useEffect(() => {
        configApi.keycloak().then(setHorreumOidcConfig)
    }, []);

    if (!horreumOidcConfig) {
        return <Bullseye>
            <Spinner/>
        </Bullseye>
    }

    // if using oidc let's wrap the entire app with AuthProvider
    const userManager = createUserManager(horreumOidcConfig)
    return (
        <AuthProvider userManager={userManager} onSigninCallback={onSigninCallback}>
            {/* if url is empty or null -> use basic authentication */}
            <AuthBridgeContextProvider isOidc={horreumOidcConfig.url !== undefined && horreumOidcConfig.url !== ""}>
                <AppContextProvider>
                    <RouterProvider router={router}/>
                </AppContextProvider>
            </AuthBridgeContextProvider>
        </AuthProvider>
    )

}

function Main() {
    const { isManager, isAdmin } = useContext(AuthBridgeContext) as AuthContextType;

    const [sidebarOpen, setSidebarOpen] = useState(window.sessionStorage.getItem("sidebarOpen")?.toLowerCase() === "true");

    useEffect(() => {
        window.sessionStorage.setItem("sidebarOpen", sidebarOpen.toString())
    }, [sidebarOpen]);

    const headerToolbar = (
        <Toolbar id="header-toolbar">
            <ToolbarContent>
                <ToolbarGroup align={{ default: 'alignEnd' }}>
                    <ToolbarItem>
                        <UserProfileLink/>
                    </ToolbarItem>
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
                {(isAdmin() || isManager() ) && (
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
