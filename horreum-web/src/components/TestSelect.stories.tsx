import type { Meta, StoryObj } from '@storybook/react';
import TestSelect from './TestSelect';
//needed to render
import { Access } from "../api"
import ContextProvider from '../context/appContext'
import store from "../store"
import {Provider, useSelector} from "react-redux"
// import ContextProvider, {history} from "../context/appContext";
const meta = {
    title: "components/TestSelect",
    component: TestSelect,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    // decorators: [
    //     (Story) => (<Provider store={store}><ContextProvider><Story/></ContextProvider></Provider>),
    // ],
    tags: ['autodocs'],
} satisfies Meta<typeof TestSelect>;
export default meta;
type Story = StoryObj<typeof meta>;

//tries to fetch /api/test/summary?folder=* which means it has side-effects and isn't a component
//looks like a useMemo fetchTests
export const Simple: Story = {
    args: {        
        tests: [
            {id: 10, name: "test10", notificationsEnabled: false, access: Access.Public, owner: "me"}
        ]
    },
    render: function Render(args){
        return (<Provider store={store}><ContextProvider><TestSelect {...args} /></ContextProvider></Provider>)
    }
}
