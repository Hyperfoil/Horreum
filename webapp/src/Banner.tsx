import { useEffect, useState } from "react"

import { Alert } from "@patternfly/react-core"

import { fetchApi } from "./services/api/index"

function fetchBanner() {
    return fetchApi("/api/banner", null, "get")
}

type BannerData = {
    severity: "danger" | "warning" | "info"
    title: string
    message: string
}

export default function Banner() {
    const [banner, setBanner] = useState<BannerData>()
    const [updateCounter, setUpdateCounter] = useState(0)
    useEffect(() => {
        setTimeout(() => setUpdateCounter(updateCounter + 1), 60000)
        fetchBanner().then(setBanner)
    }, [updateCounter])
    if (!banner) {
        return null
    }
    return (
        <Alert variant={banner.severity} title={banner.title} isInline>
            <div dangerouslySetInnerHTML={{ __html: banner.message }}></div>
        </Alert>
    )
}
