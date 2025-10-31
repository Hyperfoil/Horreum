// This effect syncs the React auth state to your static AuthService
import * as React from "react";
import {AuthContextType} from "./@types/authContextTypes";
import {useAuth} from "react-oidc-context";
import {useEffect, useState} from "react";
import {userApi, UserData} from "../api";
import {Bullseye, Spinner} from "@patternfly/react-core";

// this simple object acts as a bridge for api middleware.
export interface IAuthBridge {
    isOidc: boolean;
    accessToken: string | undefined;
    setToken: (token: string | undefined) => void;
    setIsOidc: (isOidc: boolean) => void;
}

export const AuthBridge: IAuthBridge = {
    isOidc: true,
    accessToken: undefined,
    setToken: (token) => {
        AuthBridge.accessToken = token;
    },
    setIsOidc: (isOidc) => {
        AuthBridge.isOidc = isOidc;
    },
};

function isTester(owner: string, roles: string[]) {
    return roles.includes(owner.slice(0, -4) + "tester")
}

export const AuthBridgeContext = React.createContext<AuthContextType | null>(null);
export const beforeLoginHistorySession = "beforeLoginPath"

const signOutCallback = () => window.location.replace(window.location.origin)

type ContextProviderProps = {
    isOidc: boolean;
    children: React.ReactNode;
};

const AuthBridgeContextProvider: React.FC<ContextProviderProps> = ({ isOidc, children }) => {
    const auth = useAuth();

    const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
    const [name, setName] = useState<string | undefined>();
    const [username, setUsername] = useState<string | undefined>();
    const [token, setToken] = useState<string | undefined>();
    const [isBridgeTokenSet, setIsBridgeTokenSet] = useState<boolean>(false);
    const [roles, setRoles] = useState<string[]>([]);
    const [defaultTeam, setDefaultTeam] = useState<string|undefined>(undefined);

    // set the isOidc property bridge to let the api middleware fetch it
    AuthBridge.setIsOidc(isOidc)

    const signIn = isOidc ?
        () => {
            // save the current pathname in the local session to let the callback page redirect back
            window.sessionStorage.setItem(beforeLoginHistorySession, window.location.pathname)
            return auth.signinRedirect()
        } :
        (username?: string, password?: string) => {
            setIsAuthenticated(true)
            setToken(window.btoa(username + ':' + password))
            setUsername(username)
            return Promise.resolve()
        };

    const signOut = isOidc ?
        () => auth.removeUser().then(signOutCallback) :
        () => {
            setToken(undefined)
            setIsAuthenticated(false)
            return Promise.resolve().then(signOutCallback)
        };

    useEffect(() => {
        if (isOidc) {
            setIsAuthenticated(auth.isAuthenticated);
            setToken(auth.user?.access_token)
            setUsername(auth.user?.profile.preferred_username)
            setName(auth.user?.profile.name)
        }
    }, [auth.isAuthenticated]);

    useEffect(() => {
        AuthBridge.setToken(token);
        setIsBridgeTokenSet(token !== undefined);
    }, [token]);

    useEffect(() => {
        if (isBridgeTokenSet) {
            userApi.getRoles().then(
                roles => {
                    setRoles(roles);
                    userApi.defaultTeam().then(
                        team => setDefaultTeam(team || undefined),
                        error => console.log(error)
                    )

                    if (!isOidc) {
                        // we also need to fetch the user data as not coming from oidc
                        userApi.searchUsers(username!).then(
                            userData => {
                                const user: UserData | undefined = userData.filter(u => u.username == username).at(0)
                                setName(user?.firstName + " " + user?.lastName)
                            },
                            error => console.log(error)
                        )
                    }
                },
                error => console.log(error)
            )
        } else {
            // reset
            setRoles([])
        }
    }, [isBridgeTokenSet]);

    const teams = roles.filter(role => role.endsWith("-team")).sort()
    const managedTeams = roles.filter(role => role.endsWith("-manager")).map(role => role.slice(0, -7) + "team")

    const authCtx : AuthContextType = {
        isOidc: isOidc,
        isAuthenticated: isAuthenticated,
        name: name,
        username: username,
        token: token,
        roles: roles,
        teams: teams,
        managedTeams: managedTeams,
        defaultTeam: defaultTeam ?? (teams.length > 0 ? teams[0] : undefined),
        isTester: (owner?: string) => roles.includes("tester") && (!owner || isTester(owner, roles)),
        isManager: () => managedTeams.length > 0,
        isAdmin: () => roles.includes("admin"),
        signIn: signIn,
        signOut: signOut,
    };

    // if (isOidc && auth.isLoading) {
    //     // we need this to let auth properly complete the authentication
    //     // otherwise we are too fast
    //     return (
    //         <Bullseye>
    //             <Spinner/>
    //         </Bullseye>
    //     )
    // }

    return <AuthBridgeContext.Provider value={ authCtx }>{children}</AuthBridgeContext.Provider>;
};

export default AuthBridgeContextProvider;

