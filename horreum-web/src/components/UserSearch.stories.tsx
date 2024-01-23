import type { Meta, StoryObj } from '@storybook/react';
import UserSearch from './UserSearch';
//needed to render
import { Access } from "../api"
import ContextProvider from '../context/appContext'
import store from "../store"
import {Provider, useSelector} from "react-redux"
// import ContextProvider, {history} from "../context/appContext";
const meta = {
    title: "components/UserSearch",
    component: UserSearch,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    decorators: [
        (Story) => (<Provider store={store}><ContextProvider><Story/></ContextProvider></Provider>),
    ],
    tags: ['autodocs'],
} satisfies Meta<typeof UserSearch>;
export default meta;
type Story = StoryObj<typeof meta>;

//errors when typing because directly calls fetch from /api/user/search?query=...
export const Simple: Story = {
    args: {
        // eslint-disable-next-line
        onUsers: (users)=>{},
    },
}
