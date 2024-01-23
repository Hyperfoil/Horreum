import type { Meta, StoryObj } from '@storybook/react';
import NameUri from './NameUri';
//needed to render :(
import {Router} from "react-router-dom"
import {createBrowserHistory} from "history";

const meta = {
    title: "components/NameUri",
    component: NameUri,
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
        (Story) => (<Router history={createBrowserHistory()}><Story/></Router>),
    ],

    tags: ['autodocs'],
    argTypes: {}
} satisfies Meta<typeof NameUri>;
export default meta;
type Story = StoryObj<typeof meta>;

//also needs alerting :(
export const Default: Story = {
    args: {
        //SchemaDescriptor
        descriptor: {
            id: 10,
            name: "name",
            uri: "uri"
        }        
    }
}
export const Link: Story = {
    args: {
        isLink: true,
        //SchemaDescriptor
        descriptor: {
            id: 10,
            name: "name",
            uri: "uri"
        }
    }
}
