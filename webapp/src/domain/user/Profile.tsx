import { useSelector } from "react-redux"

import { keycloakSelector } from "../../auth"

import { Button, Form, FormGroup } from "@patternfly/react-core"

import TeamSelect, { Team } from "../../components/TeamSelect"

type ProfileProps = {
    defaultRole: Team
    onDefaultRoleChange(role: Team): void
}

export default function Profile(props: ProfileProps) {
    const keycloak = useSelector(keycloakSelector)
    return (
        <Form isHorizontal={true} style={{ marginTop: "20px", width: "100%" }}>
            {keycloak && (
                <FormGroup label="Account management" fieldId="account">
                    <Button
                        onClick={() => {
                            window.location.href = keycloak.createAccountUrl({ redirectUri: window.location.href })
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
