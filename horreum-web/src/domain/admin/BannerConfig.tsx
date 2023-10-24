import { useEffect, useState } from "react"
import { useDispatch, useSelector } from "react-redux"

import {
    ActionGroup,
    Button,
    Form,
    FormGroup,
    FormSelect,
    FormSelectOption,
    Spinner,
    TextInput,
} from "@patternfly/react-core"

import Editor from "../../components/Editor/monaco/Editor"
import { alertAction, dispatchInfo } from "../../alerts"
import { isAdminSelector } from "../../auth"
import {bannerApi} from "../../api"

function setBanner(severity: string, title: string, message: string) {
    return bannerApi.set({ severity, title, message, active: true })
}

export default function BannerConfig() {
    const [severity, setSeverity] = useState("danger")
    const [title, setTitle] = useState("")
    const [message, setMessage] = useState("")
    const [saving, setSaving] = useState(false)
    const isAdmin = useSelector(isAdminSelector)
    const dispatch = useDispatch()
    useEffect(() => {
        bannerApi.get().then(
            banner => {
                if (banner) {
                    setSeverity(banner.severity)
                    setTitle(banner.title)
                    setMessage(banner.message || "")
                }
            },
            e => dispatch(alertAction("FETCH BANNER", "Failed to fetch current banner", e))
        )
    }, [])
    document.title = "Banner | Horreum"
    if (!isAdmin) {
        return null
    }
    return (
        <Form isHorizontal={true} style={{ gridGap: "2px", width: "100%", paddingRight: "8px" }}>
            <FormGroup label="Severity" isRequired={true} fieldId="severity">
                <FormSelect id="severity" value={severity} onChange={setSeverity}>
                    {["danger", "warning", "info"].map((option, index) => (
                        <FormSelectOption key={index} value={option} label={option} />
                    ))}
                </FormSelect>
            </FormGroup>
            <FormGroup label="Title" isRequired={true} fieldId="title">
                <TextInput value={title || ""} isRequired type="text" id="title" name="title" onChange={setTitle} />
            </FormGroup>
            <FormGroup label="Message" fieldId="message" helperText="Detailed message in HTML">
                <div style={{ height: "300px" }}>
                    <Editor
                        value={message}
                        onChange={value => setMessage(value || "")}
                        language="html"
                        options={{
                            mode: "text/html",
                        }}
                    />
                </div>
            </FormGroup>
            <ActionGroup style={{ marginTop: 0 }}>
                <Button
                    isDisabled={saving}
                    onClick={() => {
                        setSaving(true)
                        setBanner(severity, title, message)
                            .then(
                                () => dispatchInfo(dispatch, "SET BANNER", "Banner was saved.", "", 3000),
                                e => dispatch(alertAction("SET BANNER", "Failed to set banner", e))
                            )
                            .finally(() => setSaving(false))
                    }}
                >
                    {saving ? (
                        <>
                            {"Setting up... "}
                            <Spinner size="md" />
                        </>
                    ) : (
                        "Set banner"
                    )}
                </Button>
                <Button
                    isDisabled={saving}
                    onClick={() => {
                        setSaving(true)
                        setBanner("none", "", "")
                            .then(
                                () => dispatchInfo(dispatch, "SET BANNER", "Banner was removed.", "", 3000),
                                e => dispatch(alertAction("SET BANNER", "Failed to set banner", e))
                            )
                            .finally(() => setSaving(false))
                    }}
                >
                    {saving ? (
                        <>
                            {"Removing... "}
                            <Spinner size="md" />
                        </>
                    ) : (
                        "Remove banner"
                    )}
                </Button>
            </ActionGroup>
        </Form>
    )
}
