import type { Meta, StoryObj } from '@storybook/react';
import ViewSelect from './ViewSelect';
//needed to render
import { Access } from "../api"
import ContextProvider from '../context/appContext'
import store from "../store"
import {Provider, useSelector} from "react-redux"
// import ContextProvider, {history} from "../context/appContext";
const meta = {
    title: "components/ViewSelect",
    component: ViewSelect,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    // decorators: [
    //     (Story) => (<Provider store={store}><ContextProvider><Story/></ContextProvider></Provider>),
    // ],
    tags: ['autodocs'],
} satisfies Meta<typeof ViewSelect>;
export default meta;
type Story = StoryObj<typeof meta>;

//errors when typing because directly calls fetch from /api/user/search?query=...
export const Simple: Story = {
    args: {
        viewId: 10,
        views: [
            {id: 10, name: "view10", testId: 10, components: []},
            {id: 11, name: "view11", testId: 10, components: []},
            {id: 12, name: "view12", testId: 10, components: []},
        ],
        // eslint-disable-next-line
        onChange: (viewId)=>{},
    },
}
