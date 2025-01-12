import {
    Button,
    Flex,
    FlexItem,
    Form,
    FormGroup,
    FormSelect,
    FormSelectOption,
    HelperText,
    HelperTextItem,
    FormHelperText,
    Popover,
    Radio,
    Switch,
    Tab,
    Tabs,
    TextInput,
    Title,
} from "@patternfly/react-core"
import { HelpIcon } from "@patternfly/react-icons"
import { ReactElement, useState } from "react"
import {
    Action, ActionConfig,
    HttpActionConfig as Http,
    GithubIssueCommentActionConfig as GithubIssueComment,
    GithubIssueCreateActionConfig as GithubIssueCreate,
    SlackChannelMessageActionConfig as SlackChannelMessage,
    HttpActionConfigFromJSON,
    GithubIssueCommentActionConfigFromJSON,
    GithubIssueCreateActionConfigFromJSON,
    SlackChannelMessageActionConfigFromJSON,
} from "../../api"
import HttpActionUrlSelector from "../../components/HttpActionUrlSelector"
import { CHANGE_NEW, EXPERIMENT_RESULT_NEW, TEST_NEW } from "./reducers"
import {SimpleSelect} from "@patternfly/react-templates";

function defaultConfig(type: string): ActionConfig {
  var config
  switch (type) {
    case "http":
      config = HttpActionConfigFromJSON({ url: "", type: "http" })
      break
    case "github-issue-comment":
      config = GithubIssueCommentActionConfigFromJSON({ type: "github-issue-comment" })
      break
    case "github-issue-create":
      config = GithubIssueCreateActionConfigFromJSON({ owner: "", repo: "", title: "", type: "github-issue-create" })
      break
    case "slack-channel-message":
      config = SlackChannelMessageActionConfigFromJSON({ channel: "", type: "slack-channel-message" })
      break
    default:
      config = { type }
      break
  }
  return config as ActionConfig
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
                    onChange={(_event, value) => {
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
                <SimpleSelect
                    initialOptions={[
                        {value: 'http', content: 'Generic HTTP POST request'},
                        {value: 'github-issue-comment', content: 'GitHub issue comment'},
                        {value: 'github-issue-create', content: 'GitHub issue create'},
                        {value: 'slack-channel-message', content: 'Slack channel message'}
                    ].map(option => ({... option, selected: option.value === props.action.type }))}
                    selected={props.action.type}
                    onSelect={(_, value) => update({type: value as string, config: defaultConfig(value as string)})}
                    isDisabled={!props.isTester}
                    toggleWidth="100%"
                />
            </FormGroup>
            <FormGroup label="Run always" fieldId="runAlways">
                <Switch
                    label="Enabled"
                    isChecked={props.action.runAlways}
                    onChange={(_event, runAlways) => update({ runAlways })}
                />
                <FormHelperText>
                    <HelperText>
                        <HelperTextItem>
                            Run this action even when notifications are disabled, this event is caused by a
                            recalculation etc.
                        </HelperTextItem>
                    </HelperText>
                </FormHelperText>
            </FormGroup>
            {props.action.type === "http" && (
                <HttpActionUrlSelector
                    active={props.isTester}
                    value={(props.action.config as Http)?.url || ""}
                    setValue={value => {
                        updateConfig({ url: value })
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
                                    isChecked={(props.action.config as GithubIssueComment).issueUrl !== undefined}
                                    onChange={(_event, checked) => updateConfig({ issueUrl: checked ? "" : undefined })}
                                />
                            </FlexItem>
                            <FlexItem>
                                <Radio
                                    name="issue"
                                    id="components"
                                    label="Use owner/repo/issue"
                                    isChecked={(props.action.config as GithubIssueComment).issueUrl === undefined}
                                    onChange={(_event, checked) => updateConfig({ issueUrl: checked ? undefined : "" })}
                                />
                            </FlexItem>
                        </Flex>
                    </FormGroup>

                    {(props.action.config as GithubIssueComment).issueUrl !== undefined ? (
                        <FormGroup label="Issue URL" labelIcon={<ExpressionHelp {...props} />} fieldId="issueUrl">
                            <TextInput
                                id="issueUrl"
                                value={(props.action.config as GithubIssueComment).issueUrl}
                                onChange={(_event, issueUrl) =>
                                    updateConfig({ issueUrl })
                                }
                            />
                        </FormGroup>
                    ) : (
                        <>
                            <FormGroup label="Owner" labelIcon={<ExpressionHelp {...props} />} fieldId="owner">
                                <TextInput
                                    id="owner"
                                    value={(props.action.config as GithubIssueComment).owner}
                                    onChange={(_event, owner) => updateConfig({ owner })}
                                />
                            </FormGroup>
                            <FormGroup label="Repository" labelIcon={<ExpressionHelp {...props} />} fieldId="repo">
                                <TextInput
                                    id="repo"
                                    value={(props.action.config as GithubIssueComment).repo}
                                    onChange={(_event, repo) => updateConfig({ repo })}
                                />
                            </FormGroup>
                            <FormGroup label="Issue" labelIcon={<ExpressionHelp {...props} />} fieldId="issue">
                                <TextInput
                                    id="issue"
                                    value={(props.action.config as GithubIssueComment).issue}
                                    onChange={(_event, issue) => updateConfig({ issue })}
                                />
                            </FormGroup>
                        </>
                    )}
                    <FormGroup label="Formatter" fieldId="formatter">
                        <SimpleSelect
                            initialOptions={
                                (props.action.event === EXPERIMENT_RESULT_NEW
                                        ? [{value: "experimentsResultToMarkdown", content: "Experiment result to Markdown"}]
                                        : []
                                ).map(
                                    o => ({...o, selected: o.value == (props.action.config as GithubIssueComment).formatter})
                                )
                            }
                            selected={(props.action.config as GithubIssueComment).formatter}
                            onSelect={(_, value) => updateConfig({formatter: value as string})}
                            toggleWidth="100%"
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
                            value={(props.action.config as GithubIssueCreate).owner}
                            onChange={(_event, owner) => updateConfig({ owner })}
                        />
                    </FormGroup>
                    <FormGroup label="Repository" labelIcon={<ExpressionHelp {...props} />} fieldId="repo">
                        <TextInput
                            id="repo"
                            value={(props.action.config as GithubIssueCreate).repo}
                            onChange={(_event, repo) => updateConfig({ repo })}
                        />
                    </FormGroup>
                    <FormGroup label="Title" labelIcon={<ExpressionHelp {...props} />} fieldId="title">
                        <TextInput
                            id="title"
                            value={(props.action.config as GithubIssueCreate).title}
                            onChange={(_event, title) => updateConfig({ title })}
                        />
                    </FormGroup>
                    <FormGroup label="Formatter" fieldId="formatter">
                        <SimpleSelect
                            initialOptions={
                                (props.action.event === CHANGE_NEW
                                        ? [{value: "changeToMarkdown", content: "Change to Markdown"}]
                                        : []
                                ).map(
                                    o => ({...o, selected: o.value === (props.action.config as GithubIssueCreate).formatter})
                                )
                            }
                            selected={(props.action.config as GithubIssueCreate).formatter}
                            onSelect={(_, value) => updateConfig({formatter: value as string})}
                            toggleWidth="100%"
                        />
                    </FormGroup>
                </>
            )}
            {props.action.type === "slack-channel-message" && (
                <>
                    <SlackTokenInput
                        helpIcon={<ExpressionHelp {...props} />}
                        secrets={props.action.secrets}
                        onChange={secrets => update({ secrets })}
                    />
                    <FormGroup label="Channel" labelIcon={<ExpressionHelp {...props} />} fieldId="channel">
                        <TextInput
                            id="channel"
                            value={(props.action.config as SlackChannelMessage).channel}
                            onChange={(_event, channel) => updateConfig({ channel })}
                        />
                    </FormGroup>
                    <FormGroup label="Formatter" fieldId="formatter">
                        <SimpleSelect
                            initialOptions={
                                (props.action.event === CHANGE_NEW
                                        ? [{value: "changeToMarkdown", content: "Change to Markdown"}]
                                        : props.action.event === EXPERIMENT_RESULT_NEW
                                            ? [{value: "experimentsResultToMarkdown", content: "Experiment result to Markdown"}]
                                            : props.action.event === TEST_NEW
                                                ? [{value: "testToSlack", content: "Test to Slack Markdown"}]
                                                : []
                                ).map(
                                    o => ({...o, selected: o.value == (props.action.config as SlackChannelMessage).formatter})
                                )
                            }
                            selected={(props.action.config as SlackChannelMessage).formatter}
                            onSelect={(_, value) => updateConfig({formatter: value as string})}
                            toggleWidth="100%"
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
                onChange={(_event, token) => props.onChange({ token, modified: true })}
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

type SlackTokenInputProps = {
    helpIcon: ReactElement
    secrets: any
    onChange(secrets: any): void
}

function SlackTokenInput(props: SlackTokenInputProps) {
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
                onChange={(_event, token) => props.onChange({ token, modified: true })}
            />
            See{" "}
            <a href="https://api.slack.com/authentication/oauth-v2" target="_blank">
                Slack API OAuth authentication
            </a>{" "}
            for more info about authenticating Slack apps with OAuth tokens.
        </FormGroup>
    )
}
