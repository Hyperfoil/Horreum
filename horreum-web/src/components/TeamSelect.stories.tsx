import type { Meta, StoryObj } from '@storybook/react';
import TeamSelect from './TeamSelect';
//needed to render
import store from "../store"
import {Provider, useSelector} from "react-redux"
import ContextProvider, {history} from "../context/appContext";
const meta = {
    title: "components/TeamSelect",
    component: TeamSelect,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    decorators: [
        (Story) => (<Provider store={store}><ContextProvider><Story/></ContextProvider></Provider>),
    ],
    tags: ['autodocs'],
} satisfies Meta<typeof TeamSelect>;
export default meta;
type Story = StoryObj<typeof meta>;

//TODO doesn't render any teams because relies on context and we don't pre-populate
export const ExcludeGeneral: Story = {
    args: {        
        includeGeneral: false,
        selection: "",
    }
}

export const IncludeGeneral: Story = {
    args: {        
        includeGeneral: true,
        selection: "",
    }
}
