import { useEffect, useState } from "react"

import { Alert } from "@patternfly/react-core"
import Api, { Banner as BannerData } from "./api"

export default function Banner() {
    const [banner, setBanner] = useState<BannerData>()
    const [updateCounter, setUpdateCounter] = useState(0)
    useEffect(() => {
        setTimeout(() => setUpdateCounter(updateCounter + 1), 60000)
        Api.bannerServiceGet().then(setBanner)
    }, [updateCounter])
    if (!banner) {
        return null
    }
    return (
        <Alert variant={banner.severity as any} title={banner.title} isInline>
            <div dangerouslySetInnerHTML={{ __html: banner.message || "" }}></div>
        </Alert>
    )
}
