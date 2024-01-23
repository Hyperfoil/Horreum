import type { Meta, StoryObj } from '@storybook/react';
import ChangeAccessModal from './ChangeAccessModal';

//Things we have to import because of component dependencies
import { Access } from "../api"
import {Provider, useSelector} from "react-redux"
import store from "../store"

const meta = {
    title: "components/ChangeAccessModal",
    component: ChangeAccessModal,
    parameters: {
        // Optional parameter to center the component in the Canvas. More info: https://storybook.js.org/docs/configure/story-layout
        layout: 'centered',
    },
    decorators: [
        (Story) => (<Provider store={store}><Story/></Provider>),
    ],
    // This component will have an automatically generated Autodocs entry: https://storybook.js.org/docs/writing-docs/autodocs
    tags: ['autodocs'],
    // More on argTypes: https://storybook.js.org/docs/api/argtypes
    argTypes: {
        
    },  
} satisfies Meta<typeof ChangeAccessModal>;
export default meta;
type Story = StoryObj<typeof meta>;

//render error because it tries to fetch the selection with teamToName
export const Open: Story = {
    args: {
        isOpen: true,
        owner: "owner",
        access: Access.Public
    },
}