import type { Meta, StoryObj } from '@storybook/react';
import LogSlider from './LogSlider';
//needed to render :(
import store from "../store"
import {Provider, useSelector} from "react-redux"
import ContextProvider, {history} from "../context/appContext";

const meta = {
    title: "components/LogSlider",
    component: LogSlider,
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
} satisfies Meta<typeof LogSlider>;
export default meta;
type Story = StoryObj<typeof meta>;

//also needs alerting :(
export const Default: Story = {
    args: {
        value: 10,
        // eslint-disable-next-line
        onChange: (value)=>{},
    }
}
//ConditionComponent.tsx
export const Discrete: Story = {
    args: {
        value: 10,
        // eslint-disable-next-line
        onChange: (value)=>{},
        isDisabled: false,
        isDiscrete: true
    }
}
export const Disabled: Story = {
    args: {
        value: 10,
        // eslint-disable-next-line
        onChange: (value)=>{},
        isDisabled: true,
    }    
}
