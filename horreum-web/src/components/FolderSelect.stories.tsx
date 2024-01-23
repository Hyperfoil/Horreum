
import type { Meta, StoryObj } from '@storybook/react';
import FolderSelect from './FolderSelect';
//required for rendering
import store from "../store"
import {Provider, useSelector} from "react-redux"
import ContextProvider, {history} from "../context/appContext";
const meta = {
    title: "components/FolderSelect",
    component: FolderSelect,
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
    argTypes: {

    }
} satisfies Meta<typeof FolderSelect>;
export default meta;
type Story = StoryObj<typeof meta>;

export const CanCreate: Story = {
    args: {
        folder: "folder",
        canCreate: true,
        readOnly: false,
        // eslint-disable-next-line
        onChange: (folder)=>{}
    }
}

//ERROR cannot render because depends on react useContext :( for alerting
//The use context all over the place 
// export const Default: Story = {
//     args: {
//         name: "name",
//         // eslint-disable-next-line
//         export: async ()=>async ()=>{}
//     },
// }
