import { NavLink, NavLinkProps } from "react-router-dom"

export default function ButtonLink(
    props: NavLinkProps & { variant?: "primary" | "secondary" | "tertiary" | "control"; isDisabled?: boolean }
) {
    const variant = props.variant || "primary"
    return <NavLink className={"pf-v5-c-button pf-m-" + variant + (props.isDisabled ? " pf-m-disabled" : "")} {...props} />
}
