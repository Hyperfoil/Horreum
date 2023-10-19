import {
    Button,
    Form,
    FormGroup,
    Modal,
    Tab,
    Tabs,
    TextInput
} from "@patternfly/react-core";
import React, {useContext, useMemo, useState} from "react";
import {noop} from "../../utils";
import AccessChoice from "../../components/AccessChoice";
import {Access as authAccess, Test} from "../../generated";
import Editor from "../../components/Editor/monaco/Editor";
import {apiCall, runApi} from "../../api";
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";

interface RunImportModalProps {
    onClose(): void;

    isOpen: boolean;
    test?: Test;
    owner: string;
}


export const RunImportModal = (props: RunImportModalProps) => {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [start, setStart] = useState("")
    const [stop, setStop] = useState("")
    const [schemaUrn, setSchemaUrn] = useState("")
    const [access, setAccess] = useState<authAccess>(props.test?.access || authAccess.Public)
    const [payloadData, setPayloadData] = useState<string | undefined>()


    const [activeTabKey, setActiveTabKey] = React.useState<string | number>(0);
    const [isBox] = React.useState<boolean>(false);
    // Toggle currently active tab
    const handleTabClick = (
        event: React.MouseEvent<any> | React.KeyboardEvent | MouseEvent,
        tabIndex: string | number
    ) => {
        setActiveTabKey(tabIndex);
    };

    const importRun = () => {

         apiCall(runApi.addRunFromData(
                 start,
                 stop,
                 props.test?.name || "",
                 access,
                 undefined,
                 props.owner,
                 schemaUrn,
                 undefined,
                  JSON.parse(payloadData || "")
             )
             , alerting, "UPLOAD_ERROR", "Failed to upload run data")
             .then(noop)
             .then(() => props.onClose())

    }


    const actionButtons = [
        <Button variant="primary" onClick={importRun}>Save</Button>,
        <Button variant="link" onClick={props.onClose}>Cancel</Button>
    ]

    const memoizedEditor = useMemo(() => {
        // TODO: height 100% doesn't work
        return (
            <Editor
                height="600px"
                value={payloadData}
                options={{
                    mode: "application/ld+json",
                    readOnly: false,
                }}
                onChange={setPayloadData}
            />
        )
    }, [payloadData])

    return (
        <Modal variant="large" title="Import New Run" actions={actionButtons} isOpen={props.isOpen}
               onClose={props.onClose}>
            <Tabs
                activeKey={activeTabKey}
                onSelect={handleTabClick}
                isBox={isBox}
                aria-label="Tabs in the default example"
                role="region"
            >
                <Tab
                    key={0}
                    eventKey={0}
                    title="Metadata"
                >
                        <Form isHorizontal>
                            <FormGroup
                                label="name"
                                isRequired
                                fieldId="horizontal-form-name"
                            >
                                <TextInput value={props.test?.name || ""} isDisabled={true}/>
                            </FormGroup>
                            <FormGroup
                                label="owner"
                                isRequired
                                fieldId="horizontal-form-owner"
                                helperText="Owner of the run data"
                            >
                                <TextInput value={props.owner} isDisabled={true}/>

                            </FormGroup>
                            <FormGroup
                                label="start"
                                isRequired
                                fieldId="horizontal-form-start"
                                helperText="Please provide a timestamp for the start of the run, or a json path expression to extract it from the data.
                                 e.g. `2023-11-15T10:11:43.896Z` or `$.start`"
                            >
                                <TextInput value={start} onChange={setStart}/>
                            </FormGroup>
                            <FormGroup
                                label="stop"
                                isRequired
                                fieldId="horizontal-form-username"
                                helperText="Please provide a timestamp for the end of the run, or a json path expression to extract it from the data.
                                 e.g. `2023-11-15T10:11:43.896Z` or `$.stop`"
                            >
                                <TextInput value={stop} onChange={setStop}/>
                            </FormGroup>
                            <FormGroup
                                label="schema"
                                isRequired
                                fieldId="horizontal-form-schema"
                                helperText="schema URN for the run data"
                            >
                                <TextInput value={schemaUrn} onChange={setSchemaUrn}/>
                            </FormGroup>
                            <FormGroup
                                label="access"
                                isRequired
                                fieldId="horizontal-form-accerss"
                                helperText="Visbility of run data"
                            >
                                <AccessChoice
                                    checkedValue={access}
                                    onChange={a => {
                                        setAccess(a)
                                    }}
                                />
                            </FormGroup>
                        </Form>
                </Tab>
                <Tab
                    key={1}
                    eventKey={1}
                    title="Payload"
                >
                    {memoizedEditor}
                </Tab>
            </Tabs>

        </Modal>


    )
}