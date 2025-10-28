
export type AuthContextType = {
    isOidc: boolean;
    isAuthenticated: boolean;
    name?: string;
    username?: string;
    token?: string;
    roles: string[];
    teams: string[];
    managedTeams: string[];
    defaultTeam?: string;
    isTester: (owner?: string) => boolean;
    isManager: () => boolean;
    isAdmin: () => boolean;
    // login / logout
    signIn: (username?: string, password?: string) => Promise<void>;
    signOut: () => Promise<void>;
}