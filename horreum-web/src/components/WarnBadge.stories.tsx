import type { Meta, StoryObj } from '@storybook/react';
import WarnBadge from "./WarnBadge"
//needed to render
import { Access } from "../api"
import ContextProvider from '../context/appContext'
import store from "../store"
import {Provider, useSelector} from "react-redux"
// import ContextProvider, {history} from "../context/appContext";
const meta = {
    title: "components/WarnBadge",
    component: WarnBadge,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    // decorators: [
    //     (Story) => (<Provider store={store}><ContextProvider><Story/></ContextProvider></Provider>),
    // ],
    tags: ['autodocs'],
} satisfies Meta<typeof WarnBadge>;
export default meta;
type Story = StoryObj<typeof meta>;

//errors when typing because directly calls fetch from /api/user/search?query=...
export const Text: Story = {
    args: {
        children: "text warning"
    },
}
export const Number: Story = {
    args: {
        children: 42
    },
}
export const Element: Story = {
    args: {
        children: <span>span of text</span>
    },
}
