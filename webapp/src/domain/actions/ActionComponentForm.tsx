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
import { ReactElement, useState } from "react"

import { Action } from "../../api"
import EnumSelect from "../../components/EnumSelect"
import HttpActionUrlSelector from "../../components/HttpActionUrlSelector"
import { CHANGE_NEW, EXPERIMENT_RESULT_NEW } from "./reducers"

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
                    options={{
                        http: "Generic HTTP POST request",
                        "github-issue-comment": "GitHub issue comment",
                        "github-issue-create": "Create GitHub issue",
                    }}
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
            {props.action.type === "github-issue-comment" && (
                <>
                    <GitHubTokenInput
                        helpIcon={<ExpressionHelp {...props} />}
                        secrets={props.action.secrets}
                        onChange={secrets => update({ secrets })}
                    />
                    <FormGroup label="Issue source" labelIcon={<ExpressionHelp {...props} />} fieldId="issue">
                        <Flex style={{ paddingTop: "14px" }}>
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
                    </FormGroup>

                    {props.action.config.issueUrl !== undefined ? (
                        <FormGroup label="Issue URL" labelIcon={<ExpressionHelp {...props} />} fieldId="issueUrl">
                            <TextInput
                                id="issueUrl"
                                value={props.action.config.issueUrl}
                                onChange={issueUrl => update({ config: { ...props.action.config, issueUrl } })}
                            />
                        </FormGroup>
                    ) : (
                        <>
                            <FormGroup label="Owner" labelIcon={<ExpressionHelp {...props} />} fieldId="owner">
                                <TextInput
                                    id="owner"
                                    value={props.action.config.owner}
                                    onChange={owner => updateConfig({ owner })}
                                />
                            </FormGroup>
                            <FormGroup label="Repository" labelIcon={<ExpressionHelp {...props} />} fieldId="repo">
                                <TextInput
                                    id="repo"
                                    value={props.action.config.repo}
                                    onChange={repo => updateConfig({ repo })}
                                />
                            </FormGroup>
                            <FormGroup label="Issue" labelIcon={<ExpressionHelp {...props} />} fieldId="issue">
                                <TextInput
                                    id="issue"
                                    value={props.action.config.issue}
                                    onChange={issue => updateConfig({ issue })}
                                />
                            </FormGroup>
                        </>
                    )}
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
            {props.action.type === "github-issue-create" && (
                <>
                    <GitHubTokenInput
                        helpIcon={<ExpressionHelp {...props} />}
                        secrets={props.action.secrets}
                        onChange={secrets => update({ secrets })}
                    />
                    <FormGroup label="Owner" labelIcon={<ExpressionHelp {...props} />} fieldId="owner">
                        <TextInput
                            id="owner"
                            value={props.action.config.owner}
                            onChange={owner => updateConfig({ owner })}
                        />
                    </FormGroup>
                    <FormGroup label="Repository" labelIcon={<ExpressionHelp {...props} />} fieldId="repo">
                        <TextInput
                            id="repo"
                            value={props.action.config.repo}
                            onChange={repo => updateConfig({ repo })}
                        />
                    </FormGroup>
                    <FormGroup label="Title" labelIcon={<ExpressionHelp {...props} />} fieldId="title">
                        <TextInput
                            id="title"
                            value={props.action.config.title}
                            onChange={title => updateConfig({ title })}
                        />
                    </FormGroup>
                    <FormGroup label="Formatter" fieldId="formatter">
                        <EnumSelect
                            options={
                                props.action.event === CHANGE_NEW ? { changeToMarkdown: "Change to Markdown" } : {}
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

type GitHubTokenInputProps = {
    helpIcon: ReactElement
    secrets: any
    onChange(secrets: any): void
}

function GitHubTokenInput(props: GitHubTokenInputProps) {
    return (
        <FormGroup label="Token" labelIcon={props.helpIcon} fieldId="token">
            <TextInput
                id="token"
                value={props.secrets.token || ""}
                onFocus={() => {
                    if (!props.secrets.modified) {
                        props.onChange({ token: "" })
                    }
                }}
                onBlur={() => {
                    if (!props.secrets.token && !props.secrets.modified) {
                        props.onChange({ token: "********" })
                    }
                }}
                onChange={token => props.onChange({ token, modified: true })}
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
    )
}
