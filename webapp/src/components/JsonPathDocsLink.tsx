import {
    Tooltip
} from '@patternfly/react-core'
import {
    HelpIcon
} from '@patternfly/react-icons'

export default function JsonPathDocsLink() {
    return (<Tooltip position="right" content={<span>PostgreSQL JSON path documentation</span>}>
        <a style={{ padding: "5px 8px" }} target="_blank" rel="noopener noreferrer"
        href="https://www.postgresql.org/docs/12/functions-json.html#FUNCTIONS-SQLJSON-PATH">
        <HelpIcon />
        </a>
    </Tooltip>)
}