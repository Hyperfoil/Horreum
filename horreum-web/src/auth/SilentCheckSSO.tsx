import {Bullseye, Spinner} from "@patternfly/react-core";
import {useAuth} from "react-oidc-context";
import {useNavigate} from "react-router-dom";

// Silent check sso component, which redirects to the home page when the login is completed
function SilentCheckSSO() {
    const auth = useAuth()
    const navigate = useNavigate();

    if (auth.isLoading) {
        return (
            <Bullseye>
                <Spinner/>
            </Bullseye>
        );
    } else {
        // redirect only once the authentication stopped loading
        navigate("/");
    }
}

export default SilentCheckSSO;
