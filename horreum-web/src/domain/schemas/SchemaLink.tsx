import { useDispatch } from "react-redux"

import Api from "../../api"
import { dispatchError } from "../../alerts"
import IndirectLink from "../../components/IndirectLink"

type SchemaLinkProps = {
    uri: string
}

export default function SchemaLink({ uri }: SchemaLinkProps) {
    const dispatch = useDispatch()
    return (
        <IndirectLink
            style={{ padding: 0, fontWeight: "var(--pf-global--link--FontWeight)" }}
            onNavigate={() =>
                Api.schemaServiceIdByUri(uri).then(
                    id => `/schema/${id}`,
                    error =>
                        dispatchError(dispatch, error, "FIND_SCHEMA", "Cannot find schema with URI " + uri).then(
                            _ => ""
                        )
                )
            }
        >
            {uri}
        </IndirectLink>
    )
}
