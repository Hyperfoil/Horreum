import { NavLink } from "react-router-dom"
import { SchemaDescriptor } from "../api"

type NameUriProps = {
    isLink?: boolean
    onNavigate?(): void
    descriptor: SchemaDescriptor
}

export default function NameUri({isLink, onNavigate, descriptor}: NameUriProps) {
    const id = descriptor.id
    const name = descriptor.name
    return (
            //using id in a boolean operation means it won't be a link if id == 0
        <>        
            {id && isLink ? (
                <NavLink to={`/schema/${id}#labels`} onClick={() => onNavigate && onNavigate()}>
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
                <code>{descriptor.uri}</code>
            </span>
        </>
    )
}
