import {Alert} from "../../alerts";

export type AlertContextType = {

    alerts: Alert[];

    clearAlert: (alert: Alert) => void;

    dispatchError: (error: any,
                    type: string,
                    title: string,
                    ...errorFormatter: ((error: any) => any)[]) => Promise<any>;

    dispatchInfo: (type: string,
                   title: string,
                   message: string,
                   timeout: number) => Promise<any>;

}

export type AuthContextType = {
    updateDefaultTeam: (team: string,
                        onSuccess: () => void,
                        onFailure: (error: any) => void) => Promise<any>;
}


export type AppContextType = {
    alerting: AlertContextType;
    auth: AuthContextType;
};
