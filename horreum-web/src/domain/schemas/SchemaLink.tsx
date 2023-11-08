import { schemaApi } from "../../api"
import IndirectLink from "../../components/IndirectLink"
import {useContext} from "react";
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";

type SchemaLinkProps = {
    uri: string
}

export default function SchemaLink({ uri }: SchemaLinkProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    return (
        <IndirectLink
            style={{ padding: 0, fontWeight: "var(--pf-global--link--FontWeight)" }}
            onNavigate={() =>
                schemaApi.idByUri(uri).then(
                    id => `/schema/${id}`,
                    error => alerting.dispatchError(error, "FIND_SCHEMA", "Cannot find schema with URI " + uri)
                ).then()
            }
        >
            {uri}
        </IndirectLink>
    )
}
