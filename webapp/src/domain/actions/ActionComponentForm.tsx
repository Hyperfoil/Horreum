import {
    Button,
    Flex,
    FlexItem,
    Form,
    FormGroup,
    FormSelect,
    FormSelectOption,
    Popover,
    Radio,
    Tab,
    Tabs,
    TextInput,
    Title,
} from "@patternfly/react-core"
import { HelpIcon } from "@patternfly/react-icons"
import { useState } from "react"

import { Action } from "../../api"
import EnumSelect from "../../components/EnumSelect"
import HttpActionUrlSelector from "../../components/HttpActionUrlSelector"
import { EXPERIMENT_RESULT_NEW } from "./reducers"

function defaultConfig(type: string) {
    switch (type) {
        case "http":
            return { url: "" }
        case "github":
            return { issueUrl: "${$.path.to.issue.url}" }
        default:
            return {}
    }
}

type ActionComponentFormProps = {
    action: Action
    onUpdate(action: Action): void
    eventTypes: string[][]
    isTester: boolean
    setValid?(valid: boolean): void
} & Omit<React.HTMLProps<HTMLFormElement>, "action">

export default function ActionComponentForm(props: ActionComponentFormProps) {
    function update(patch: Partial<Action>) {
        props.onUpdate({ ...props.action, ...patch })
    }
    function updateConfig(patch: any) {
        update({ config: { ...props.action.config, ...patch } })
    }
    return (
        <Form isHorizontal={true}>
            <FormGroup label="Event Type" fieldId="event">
                <FormSelect
                    id="event"
                    validated={"default"}
                    value={props.action.event}
                    onChange={value => {
                        update({ event: value })
                    }}
                    aria-label="Event Type"
                >
                    {props.eventTypes.map((option, index) => {
                        return <FormSelectOption key={index} value={option[0]} label={option[0]} />
                    })}
                </FormSelect>
            </FormGroup>
            <FormGroup label="Action type" fieldId="type">
                <EnumSelect
                    options={{ http: "Generic HTTP POST request", github: "Github issue comment" }}
                    selected={props.action.type}
                    onSelect={type => {
                        update({ type, config: defaultConfig(type) })
                    }}
                    isDisabled={!props.isTester}
                />
            </FormGroup>
            {props.action.type === "http" && (
                <HttpActionUrlSelector
                    active={props.isTester}
                    value={props.action.config?.url || ""}
                    setValue={value => {
                        update({ config: { url: value } })
                    }}
                    isReadOnly={!props.isTester}
                    setValid={props.setValid}
                />
            )}
            {props.action.type === "github" && (
                <>
                    <FormGroup label="Token" labelIcon={<ExpressionHelp {...props} />} fieldId="token">
                        <TextInput
                            id="token"
                            value={props.action.secrets.token || ""}
                            onFocus={() => {
                                if (!props.action.secrets.modified) {
                                    update({ secrets: { token: "" } })
                                }
                            }}
                            onBlur={() => {
                                if (!props.action.secrets.token && !props.action.secrets.modified) {
                                    update({ secrets: { token: "********" } })
                                }
                            }}
                            onChange={token => update({ secrets: { token, modified: true } })}
                        />
                        See{" "}
                        <a
                            href="https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token"
                            target="_blank"
                        >
                            Github Docs
                        </a>{" "}
                        for more info about tokens.
                    </FormGroup>
                    <FormGroup label="Issue" labelIcon={<ExpressionHelp {...props} />} fieldId="issue">
                        <Flex>
                            <FlexItem>
                                <Radio
                                    name="issue"
                                    id="url"
                                    label="Use issue URL"
                                    isChecked={props.action.config.issueUrl !== undefined}
                                    onChange={checked => updateConfig({ issueUrl: checked ? "" : undefined })}
                                />
                            </FlexItem>
                            <FlexItem>
                                <Radio
                                    name="issue"
                                    id="components"
                                    label="Use owner/repo/issue"
                                    isChecked={props.action.config.issueUrl === undefined}
                                    onChange={checked => updateConfig({ issueUrl: checked ? undefined : "" })}
                                />
                            </FlexItem>
                        </Flex>
                        {props.action.config.issueUrl !== undefined ? (
                            <TextInput
                                id="issueUrl"
                                value={props.action.config.issueUrl}
                                onChange={issueUrl => update({ config: { ...props.action.config, issueUrl } })}
                            />
                        ) : (
                            <>
                                Owner:{" "}
                                <TextInput
                                    id="owner"
                                    value={props.action.config.owner}
                                    onChange={owner => updateConfig({ owner })}
                                />
                                Repository:{" "}
                                <TextInput
                                    id="repo"
                                    value={props.action.config.repo}
                                    onChange={repo => updateConfig({ repo })}
                                />
                                Issue:
                                <TextInput
                                    id="issue"
                                    value={props.action.config.issue}
                                    onChange={issue => updateConfig({ issue })}
                                />
                            </>
                        )}
                    </FormGroup>
                    <FormGroup label="Formatter" fieldId="formatter">
                        <EnumSelect
                            options={
                                props.action.event === EXPERIMENT_RESULT_NEW
                                    ? { experimentResultToMarkdown: "Experiment result to Markdown" }
                                    : {}
                            }
                            selected={props.action.config.formatter}
                            onSelect={formatter => updateConfig({ formatter })}
                        />
                    </FormGroup>
                </>
            )}
        </Form>
    )
}

type ExpressionHelpProps = {
    eventTypes: string[][]
}

function ExpressionHelp(props: ExpressionHelpProps) {
    const [tab, setTab] = useState<string | number>(props.eventTypes[0][0])
    return (
        <Popover
            minWidth="50vw"
            maxWidth="50vw"
            bodyContent={
                <>
                    <Title headingLevel="h4">Using expressions</Title>
                    These text fields support replacing parts of the script with JSON Path expression, identified with{" "}
                    <code>${"{$.path.to.json.field}"}</code> syntax. This is <b>not</b> the PostgreSQL JSON Path but the
                    standard{" "}
                    <a href="https://github.com/json-path/JsonPath" target="_blank">
                        Java implementation
                    </a>
                    . The object depends on the event type, see below for examples:
                    <Tabs activeKey={tab} onSelect={(_, key) => setTab(key)}>
                        {props.eventTypes.map(([type, example]) => (
                            <Tab title={type} eventKey={type}>
                                <div style={{ maxHeight: "50vh", overflowY: "auto" }}>
                                    <code>
                                        <pre>{example}</pre>
                                    </code>
                                </div>
                            </Tab>
                        ))}
                    </Tabs>
                </>
            }
        >
            <Button variant="plain" onClick={e => e.preventDefault()}>
                <HelpIcon />
            </Button>
        </Popover>
    )
}
