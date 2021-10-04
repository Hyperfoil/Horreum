import { NavLink, NavLinkProps } from "react-router-dom"

export default function ButtonLink(
    props: NavLinkProps & { variant?: "primary" | "secondary" | "tertiary" | "control" }
) {
    const variant = props.variant || "primary"
    return <NavLink className={"pf-c-button pf-m-" + variant} {...props} />
}
