import { NavLink } from "react-router-dom"
import { SchemaDescriptor } from "../api"

type NameUriProps = {
    isLink?: boolean
    onNavigate?(): void
    descriptor: SchemaDescriptor
}

export default function NameUri(props: NameUriProps) {
    const id = props.descriptor.id
    const name = props.descriptor.name
    return (
        <>
            {id && props.isLink ? (
                <NavLink to={`/schema/${id}#labels`} onClick={() => props.onNavigate && props.onNavigate()}>
                    {name}
                </NavLink>
            ) : (
                name
            )}
            {"\u00A0"}
            <span
                style={{
                    border: "1px solid #888",
                    borderRadius: "4px",
                    padding: "4px",
                    backgroundColor: "#f0f0f0",
                    fontSize: "smaller",
                }}
            >
                <code>{props.descriptor.uri}</code>
            </span>
        </>
    )
}
