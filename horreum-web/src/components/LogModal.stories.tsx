import type { Meta, StoryObj } from '@storybook/react';
import LogModal from './LogModal';
//needed to render :(
import store from "../store"
import {Provider, useSelector} from "react-redux"
import ContextProvider, {history} from "../context/appContext";

const meta = {
    title: "components/LogModal",
    component: LogModal,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    //using the decorator to try and 
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
} satisfies Meta<typeof LogModal>;
export default meta;
type Story = StoryObj<typeof meta>;

//also needs alerting :(
export const Default: Story = {
    args: {
        title: "title",
        emptyMessage: "emptyMessage",
        isOpen: true,
        // eslint-disable-next-line
        onClose: ()=>{},
        columns: ["columnOne","columnTwo"],
        // eslint-disable-next-line
        fetchCount: (level)=>new Promise((resolve,reject)=>{}),
        // eslint-disable-next-line
        fetchLogs: (level,page,limit)=>new Promise((resolve,reject)=>{}),
        // eslint-disable-next-line
        deleteLogs: (from,to)=>new Promise((resolve,reject)=>{}),
    }
}
