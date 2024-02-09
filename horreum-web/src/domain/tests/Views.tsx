import {useContext, useEffect, useState} from "react"
import {
    Button,
    DataList,
    DataListAction,
    DataListItem,
    DataListItemRow,
    DataListItemCells,
    DataListCell,
    Form,
    FormGroup,
    TextInput,
} from "@patternfly/react-core"

import { useTester } from "../../auth"

import Labels from "../../components/Labels"
import OptionalFunction from "../../components/OptionalFunction"
import {deleteView, updateView, View, ViewComponent} from "../../api"
import { TabFunctionsRef } from "../../components/SavedTabs"
import SplitForm from "../../components/SplitForm"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import {useLocation, useNavigate} from "react-router-dom";

function swap(array: any[], i1: number, i2: number) {
    const temp = array[i1]
    array[i1] = array[i2]
    array[i2] = temp
}

type ViewComponentFormProps = {
    c: ViewComponent
    onChange(): void
    isTester: boolean
}

function checkComponent(v: ViewComponent) {
    if (!v.labels || v.labels.length === 0) {
        return "View component requires at least one label"
    } else if (v.labels.length > 1 && !v.render) {
        return "View component uses multiple labels but does not define any function to combine these."
    }
}

const ViewComponentForm = ({ c, onChange, isTester }: ViewComponentFormProps) => {
    return (
        <Form
            id={`viewcomponent-${c.id}`}
            isHorizontal={true}
        >
            <FormGroup label="Header" fieldId="header">
                <TextInput
                    value={c.headerName || ""}
                    placeholder="e.g. 'Run duration'"
                    id="header"
                    onChange={(_event, value) => {
                        c.headerName = value
                        onChange()
                    }}
                    validated={!!c.headerName && c.headerName.trim() !== "" ? "default" : "error"}
                    // isReadOnly={!isTester} no longer supported
                    readOnlyVariant={isTester ? undefined : "default"}
                />
            </FormGroup>
            <FormGroup label="Labels" fieldId="labels">
                <Labels
                    labels={c.labels}
                    onChange={labels => {
                        c.labels = labels
                        onChange()
                    }}
                    error={checkComponent(c)}
                    isReadOnly={!isTester}
                />
            </FormGroup>
            <FormGroup label="Rendering" fieldId="rendering">
                <OptionalFunction
                    func={c.render === undefined || c.render === null ? undefined : c.render.toString()}
                    onChange={value => {
                        c.render = value
                        onChange()
                    }}
                    defaultFunc="(value, dataset, token) => value"
                    addText="Add render function..."
                    undefinedText="No render function."
                    readOnly={!isTester}
                />
            </FormGroup>
        </Form>
    )
}

type ViewsProps = {
    testId: number
    views: View[]
    testOwner?: string
    funcsRef: TabFunctionsRef
    onModified(modified: boolean): void
}

type ViewExtended = View & { modified?: boolean }

function deepCopy(views: View[]): ViewExtended[] {
    const str = JSON.stringify(views, (_, val) => (typeof val === "function" ? val + "" : val))
    return JSON.parse(str) as ViewExtended[]
}

export default function Views({ testId, testOwner, funcsRef, onModified, ...props }: ViewsProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const isTester = useTester(testOwner)
    const [views, setViews] = useState<ViewExtended[]>([])
    const [deleted, setDeleted] = useState<number[]>([])
    const [selectedView, setSelectedView] = useState<ViewExtended>()
    useEffect(() => {
        // Perform a deep copy of the view object to prevent modifying store
        const copy = deepCopy(props.views)
        setViews(copy)
        if (copy.length > 0) {
            setSelectedView(copy.find(v => v.name === "Default") || copy[0])
        }
    }, [props.views])

    funcsRef.current = {
        save: () =>{
            return Promise.all([
                ...views
                    .filter(v => v.modified)
                    .map(async view => {
                        return updateView(alerting, testId, view)
                    }),
                ...deleted.map(id => { deleteView(alerting, testId, id); return null})
            ])
            //removes the output of the ...deleted.map to satisfy TypeScript type check
            .then(hasNulls => hasNulls.filter(v => v !=null) as View[])
            .then((updatedViews) => {
                //A better solution is for the parent to provide updated views props whenever views change
                const newViews = [...updatedViews]
                //add any existing views that were not updated and have valid ids
                views
                    .filter(oldView => oldView.id >= 0)//exclude any local temporary views
                    .forEach(oldView => ( 
                        newViews.some((newView : ViewExtended) => oldView.id == newView.id)
                            ? null : newViews.unshift(oldView) //use unshift to preserve view order
                        ) 
                    ) 
                setDeleted([])
                //have to update selectedView because if it was a new view (id < 0) it will no longer be in views
                if(selectedView && 'id' in selectedView){
                    const found = newViews.filter(v => v.id === selectedView.id)
                    if(found && found.length === 1){
                        setSelectedView(found[0]);
                    }
                }
                setViews(newViews);
            })
        },
        reset: () => {
            setDeleted([])
            setViews(deepCopy(props.views))
        },
    }
    const navigate = useNavigate()
    const location = useLocation()
    useEffect(() => {
        const fragmentParts = location.hash.split("+")
        if (fragmentParts.length === 3 && fragmentParts[0] === "#views") {
            const component = document.getElementById("viewcomponent-" + fragmentParts[2])
            if (component) {
                component.scrollIntoView()
            }
        }
    }, [])
    function update(update: Partial<View>) {
        if (!selectedView) {
            throw "Should not happen"
        }
        const newView = { ...selectedView, ...update, modified: true }
        setSelectedView(newView)
        setViews(views.map(v => (v.id === selectedView.id ? newView : v)))
        onModified(true)
    }
    return (
        <SplitForm<ViewExtended>
            itemType="view"
            newItem={id => ({ id, name: "", components: [] })}
            canAddItem={isTester}
            addItemText="Add view..."
            noItemTitle="No view defined (should not happen)"
            noItemText="This test does not have any view defined."
            canDelete={view => isTester && view.name !== "Default"}
            onDelete={view => {
                if (view.id && view.id > 0) {
                    setDeleted([...deleted, view.id])
                }
                setSelectedView(views[0])
            }}
            items={views}
            onChange={setViews}
            selected={selectedView}
            onSelected={setSelectedView}
            loading={false}
            actions={
                isTester && selectedView ? (
                    <Button
                        onClick={() => {
                            const components = selectedView.components
                            components.push({
                                id: -1,
                                headerName: "",
                                labels: [],
                                render: "",
                                headerOrder: components.length,
                            })
                            update({ components })
                        }}
                    >
                        Add component
                    </Button>
                ) : (
                    []
                )
            }
        >
            <FormGroup label="View name" fieldId="name">
                <TextInput
                    id="name"
                    value={selectedView?.name}
                    
                    onChange={(_event, name) => update({ name })} 
                />
            </FormGroup>
            {(!selectedView?.components || selectedView?.components.length === 0) && "The view has no components."}
            <DataList aria-label="List of variables">
                {selectedView?.components.map((c, i) => (
                    <DataListItem key={i} aria-labelledby="">
                        <DataListItemRow>
                            <DataListItemCells
                                dataListCells={[
                                    <DataListCell key="content">
                                        <ViewComponentForm
                                            c={c}
                                            onChange={() => {
                                                update({ ...selectedView })
                                            }}
                                            isTester={isTester}
                                        />
                                    </DataListCell>,
                                ]}
                            />
                            {isTester && (
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
                                        style={{ width: "51%" }}
                                        variant="control"
                                        isDisabled={i === 0}
                                        onClick={() => {
                                            swap(selectedView.components, i - 1, i)
                                            c.headerOrder = i - 1
                                            selectedView.components[i].headerOrder = i
                                            update({ ...selectedView })
                                        }}
                                    >
                                        Move up
                                    </Button>
                                    <Button
                                        style={{ width: "51%" }}
                                        variant="primary"
                                        onClick={() => {
                                            selectedView.components.splice(i, 1)
                                            selectedView.components.forEach((c, i) => (c.headerOrder = i))
                                            update({ ...selectedView })
                                        }}
                                    >
                                        Delete
                                    </Button>
                                    <Button
                                        style={{ width: "51%" }}
                                        variant="control"
                                        isDisabled={i === selectedView.components.length - 1}
                                        onClick={() => {
                                            swap(selectedView.components, i + 1, i)
                                            c.headerOrder = i + 1
                                            selectedView.components[i].headerOrder = i
                                            update({ ...selectedView })
                                        }}
                                    >
                                        Move down
                                    </Button>
                                </DataListAction>
                            )}
                        </DataListItemRow>
                    </DataListItem>
                ))}
            </DataList>
        </SplitForm>
    )
}
