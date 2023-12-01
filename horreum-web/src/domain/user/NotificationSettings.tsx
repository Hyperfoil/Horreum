import {
    Bullseye,
    Button,
    DataList,
    DataListItem,
    DataListItemRow,
    DataListItemCells,
    DataListCell,
    DataListAction,
    Form,
    FormGroup,
    HelperText,
    HelperTextItem,
    FormHelperText,    
    Spinner,
    TextInput,
    Title,
} from "@patternfly/react-core"
import NotificationMethodSelect from "../../components/NotificationMethodSelect"
import { NotificationSettings as NotificationConfig } from "../../api"

const EMPTY = { id: -1, name: "", isTeam: false, method: "", data: "", disabled: false }

type NotificationSettingsProps = {
    settings: NotificationConfig
    onChange(): void
}

const NotificationSettings = ({ settings, onChange }: NotificationSettingsProps) => {
    return (
        <Form isHorizontal={true} style={{ marginTop: "20px", width: "100%" }}>
            <FormGroup label="Method" fieldId="method">
                <NotificationMethodSelect
                    isDisabled={settings.disabled}
                    method={settings.method}
                    onChange={method => {
                        settings.method = method
                        onChange()
                    }}
                />
            </FormGroup>
            <FormGroup label="Data" fieldId="data">
                <TextInput
                    isDisabled={settings.disabled}
                    id="data"
                    value={settings.data}
                    onChange={(_event, value) => {
                        settings.data = value
                        onChange()
                    }}
                    onKeyDown={e => {
                        if (e.key === "Enter") e.preventDefault()
                    }}
                />
                <FormHelperText>
                    <HelperText>
                        <HelperTextItem>e.g. email address, IRC channel...</HelperTextItem>
                    </HelperText>
                </FormHelperText>
            </FormGroup>
        </Form>
    )
}

type NotificationSettingsListProps = {
    title: string
    data?: NotificationConfig[]
    onUpdate(data: NotificationConfig[]): void
}

export function NotificationSettingsList({ title, data, onUpdate }: NotificationSettingsListProps) {
    if (data) {
        return (
            <>
                <div
                    style={{
                        marginTop: "16px",
                        marginBottom: "16px",
                        width: "100%",
                        display: "flex",
                        justifyContent: "space-between",
                    }}
                >
                    <Title headingLevel="h3">{title}</Title>
                    <Button onClick={() => onUpdate([...data, { ...EMPTY }])}>Add notification</Button>
                </div>
                <DataList aria-label="List of settings">
                    {data.map((s, i) => (
                        <DataListItem key={i} aria-labelledby="">
                            <DataListItemRow>
                                <DataListItemCells
                                    dataListCells={[
                                        <DataListCell key="content">
                                            <NotificationSettings settings={s} onChange={() => onUpdate([...data])} />
                                        </DataListCell>,
                                    ]}
                                />
                                <DataListAction
                                    style={{
                                        flexDirection: "column",
                                        justifyContent: "center",
                                    }}
                                    id="delete"
                                    aria-labelledby="delete"
                                    aria-label="Settings actions"
                                    isPlainButtonAction
                                >
                                    <Button
                                        onClick={() => {
                                            s.disabled = !s.disabled
                                            onUpdate([...data])
                                        }}
                                    >
                                        {s.disabled ? "Enable" : "Disable"}
                                    </Button>
                                    <Button
                                        variant="secondary"
                                        onClick={() => {
                                            data.splice(i, 1)
                                            onUpdate([...data])
                                        }}
                                    >
                                        Delete
                                    </Button>
                                </DataListAction>
                            </DataListItemRow>
                        </DataListItem>
                    ))}
                </DataList>
            </>
        )
    } else {
        return (
            <Bullseye>
                <Spinner />
            </Bullseye>
        )
    }
}
