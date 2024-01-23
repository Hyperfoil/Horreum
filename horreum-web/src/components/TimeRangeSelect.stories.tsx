import type { Meta, StoryObj } from '@storybook/react';
import TimeRangeSelect from './TimeRangeSelect';
//needed to render
import { Access } from "../api"
import ContextProvider from '../context/appContext'
import store from "../store"
import {Provider, useSelector} from "react-redux"
// import ContextProvider, {history} from "../context/appContext";
const meta = {
    title: "components/TimeRangeSelect",
    component: TimeRangeSelect,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    // decorators: [
    //     (Story) => (<Provider store={store}><ContextProvider><Story/></ContextProvider></Provider>),
    // ],
    tags: ['autodocs'],
} satisfies Meta<typeof TimeRangeSelect>;
export default meta;
type Story = StoryObj<typeof meta>;

export const Simple: Story = {
    args: {
        selection: {
            from: 10,
            to: 100,
            toString: ()=>"10-100"
        },
        // eslint-disable-next-line
        onSelect: (range)=>{},
        options: [
            {from: 10,to: 100, toString: ()=>"10-100"},
            {from: 20,to: 30, toString: ()=>"10-20"},
            {from: 30,to: 40, toString: ()=>"20-30"}
        ]
    },
}
