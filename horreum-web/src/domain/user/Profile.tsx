import { Button, Form, FormGroup } from "@patternfly/react-core"

import TeamSelect, { Team } from "../../components/TeamSelect"
import {useContext} from "react";
import {AuthBridgeContext} from "../../context/AuthBridgeContext";
import {AuthContextType} from "../../context/@types/authContextTypes";
import {useAuth} from "react-oidc-context";

type ProfileProps = {
    defaultRole: Team
    onDefaultRoleChange(role: Team): void
}

export default function Profile(props: ProfileProps) {
    const { isOidc, isAuthenticated } = useContext(AuthBridgeContext) as AuthContextType;
    const auth = useAuth()

    return (
        <Form isHorizontal={true} style={{ marginTop: "20px", width: "100%" }}>
            {isOidc && isAuthenticated && (
                <FormGroup label="Account management" fieldId="account">
                    <Button
                        onClick={() => {
                            // we can safely use auth here because we are using oidc
                            window.location.href = auth.settings.authority + "/account"
                        }}
                    >
                        Manage in Keycloak...
                    </Button>
                </FormGroup>
            )}
            <FormGroup label="Default team" fieldId="defaultRole">
                <TeamSelect includeGeneral={false} selection={props.defaultRole} onSelect={props.onDefaultRoleChange} />
            </FormGroup>
        </Form>
    )
}
