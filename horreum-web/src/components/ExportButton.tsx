import {useContext, useState} from "react"
import { Button, Spinner } from "@patternfly/react-core"

import {AppContext} from "../context/appContext";
import {AppContextType} from "../context/@types/appContextTypes";
type ExportButtonProps = {
    name: string
    export(): Promise<any>
}

export default function ExportButton(props: ExportButtonProps) {
    const [downloading, setDownloading] = useState(false)
    const { alerting } = useContext(AppContext) as AppContextType;

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
                            const url = window.URL.createObjectURL(new Blob([JSON.stringify(cfg, null, 2)]))
                            const link = document.createElement("a")
                            link.href = url
                            link.setAttribute("download", `${props.name}.json`)
                            document.body.appendChild(link)
                            link.click()
                            if (link.parentNode) {
                                link.parentNode.removeChild(link)
                            }
                        },
                        error => alerting.dispatchError(error, "EXPORT", "Cannot export configuration")
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
