import { useEffect, useState } from "react"

import { Alert } from "@patternfly/react-core"
import {Banner as BannerData, bannerApi} from "./api"

function getAlertBanner(banner: BannerData) {
    return (
        <Alert variant={banner.severity as any} title={banner.title} isInline>
            <div dangerouslySetInnerHTML={{ __html: banner.message || "" }}></div>
        </Alert>
    )
}

// 30 seconds
const DEFAULT_TIMEOUT = 30000
export type TimeoutBannerProps = {
    bannerData: BannerData
    timeout?: number
    onTimeout?: () => void
}

export function TimeoutBanner({bannerData, timeout, onTimeout}: TimeoutBannerProps) {
    timeout = timeout ?? DEFAULT_TIMEOUT
    const [banner, setBanner] = useState<BannerData | undefined>(bannerData)

    useEffect(() => {
        if (banner) {
            const timeoutId = setTimeout(() => {
                setBanner(undefined)
                if (onTimeout) {
                    onTimeout()
                }
            }, timeout)

            return () => clearTimeout(timeoutId);
        }
    }, [])

    if (!banner) {
        return null
    }

    return getAlertBanner(banner)
}

export default function Banner() {
    const [banner, setBanner] = useState<BannerData>()
    const [updateCounter, setUpdateCounter] = useState(0)
    useEffect(() => {
        const timeoutId = setTimeout(() => setUpdateCounter(updateCounter + 1), 60000)
        bannerApi.get().then(setBanner)

        return () => clearTimeout(timeoutId)
    }, [updateCounter])
    if (!banner) {
        return null
    }

    return getAlertBanner(banner)
}
