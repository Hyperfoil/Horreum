import { useState } from "react"
import { useDispatch } from "react-redux"
import { Button, Spinner } from "@patternfly/react-core"

import { dispatchError } from "../alerts"
type ExportButtonProps = {
    name: string
    export(): Promise<any>
}

export default function ExportButton(props: ExportButtonProps) {
    const [downloading, setDownloading] = useState(false)
    const dispatch = useDispatch()
    return (
        <Button
            id="export"
            isDisabled={downloading}
            onClick={() => {
                setDownloading(true)
                props
                    .export()
                    .then(
                        cfg => {
                            const url = window.URL.createObjectURL(new Blob([cfg]))
                            const link = document.createElement("a")
                            link.href = url
                            link.setAttribute("download", `${props.name}.json`)
                            document.body.appendChild(link)
                            link.click()
                            if (link.parentNode) {
                                link.parentNode.removeChild(link)
                            }
                        },
                        error => dispatchError(dispatch, error, "EXPORT", "Cannot export configuration")
                    )
                    .finally(() => setDownloading(false))
            }}
        >
            Export
            {downloading && (
                <>
                    {" "}
                    <Spinner size="md" />
                </>
            )}
        </Button>
    )
}
