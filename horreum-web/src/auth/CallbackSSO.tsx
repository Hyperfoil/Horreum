import {Bullseye, Spinner} from "@patternfly/react-core";
import {useAuth} from "react-oidc-context";
import {useNavigate} from "react-router-dom";
import {AuthBridgeContext, beforeLoginHistorySession} from "../context/AuthBridgeContext";
import {useEffect, useState} from "react";

// Default callback sso component, which redirects to the last visited page when the login is completed
function CallbackSSO() {
    const auth = useAuth()
    const navigate = useNavigate();

    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        // redirect only once the authentication stopped loading
        if (!isLoading) {
            const history = window.sessionStorage.getItem(beforeLoginHistorySession) ?? "/"
            navigate(history, {replace: true});
        }
    }, [isLoading]);

    if (!auth.isLoading) {
        if (isLoading) {
            setIsLoading(false);
        }
    }

    return (
        <Bullseye>
            <Spinner/>
        </Bullseye>
    );
}

export default CallbackSSO;
