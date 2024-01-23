import type { Meta, StoryObj } from '@storybook/react';
import MaybeLoading from './MaybeLoading';
//needed to render :(
import store from "../store"
import {Provider, useSelector} from "react-redux"
import ContextProvider, {history} from "../context/appContext";

const meta = {
    title: "components/MaybeLoading",
    component: MaybeLoading,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    tags: ['autodocs'],
    argTypes: {}
} satisfies Meta<typeof MaybeLoading>;
export default meta;
type Story = StoryObj<typeof meta>;

//also needs alerting :(
export const Loading: Story = {
    args: {
        loading: true,
        children: [<>Child</>]
    }
}
export const Children: Story = {
    args: {
        loading: false,
        children: [<>Child</>]
    }
}
export const Small: Story = {
    args: {
        loading: true,
        size: "sm",
        children: [<>Child</>]
    }    
}
export const Medium: Story = {
    args: {
        loading: true,
        size: "md",
        children: [<>Child</>]
    }    
}
export const Large: Story = {
    args: {
        loading: true,
        size: "lg",
        children: [<>Child</>]
    }    
}
export const ExtraLarge: Story = {
    args: {
        loading: true,
        size: "xl",
        children: [<>Child</>]
    }    
}