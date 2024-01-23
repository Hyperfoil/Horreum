import type { Meta, StoryObj } from '@storybook/react';
import LabelsSelect from './LabelsSelect';
//needed to render :(
import store from "../store"
import {Provider, useSelector} from "react-redux"
import ContextProvider, {history} from "../context/appContext";

const meta = {
    title: "components/LabelsSelect",
    component: LabelsSelect,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    //this fixes redering with context and store but now we are sending the http requests
    //we want to mock those (we need a mockContextProvider?)
    //I don't think we can mock fetchFolders as it directly calls the api :(
    //I don't think FolderSelect should be fetching the data, it should render, not fetch
    //
    decorators: [
        (Story) => (<Provider store={store}><ContextProvider><Story/></ContextProvider></Provider>),
    ],

    tags: ['autodocs'],
    argTypes: {}
} satisfies Meta<typeof LabelsSelect>;
export default meta;
type Story = StoryObj<typeof meta>;

//Changes.tsx
export const Changes: Story = {
    args: {
        style: { width: "fit-content" },
        //TODO correct selection example
        selection: {},
        // eslint-disable-next-line
        onSelect: (seletion)=>{},
        //TODO correct source
        // eslint-disable-next-line
        source: ()=>new Promise((resolve,reject)=>{}),
        forceSplit: true
    }
}
//TestDatasets.tsx
export const TestDatssets: Story = {
    args: {
        forceSplit: true,
        fireOnPartial: true,
        showKeyHelper: true,
        addResetButton: true,
        //TODO need correct selection
        selection: {},
        // eslint-disable-next-line
        onSelect: (selection)=>{},
        //TODO correct source
        // eslint-disable-next-line
        source: ()=>new Promise((resolve,reject)=>{}),        
        emptyPlaceholder: <span>No filters available</span>
    }
}

