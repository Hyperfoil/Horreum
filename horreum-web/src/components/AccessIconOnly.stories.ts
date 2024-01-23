import type { Meta, StoryObj } from '@storybook/react';
import AccessIconOnly from './AccessIconOnly';
import { Access } from "../api"
const meta = {
    title: "components/AccessIconOnly",
    component: AccessIconOnly,
    parameters: {
        // Optional parameter to center the component in the Canvas. More info: https://storybook.js.org/docs/configure/story-layout
        layout: 'centered',
    },
    // This component will have an automatically generated Autodocs entry: https://storybook.js.org/docs/writing-docs/autodocs
    tags: ['autodocs'],
    // More on argTypes: https://storybook.js.org/docs/api/argtypes
    argTypes: {
        
    },  
} satisfies Meta<typeof AccessIconOnly>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Public: Story = {
    args: {
        access: Access.Public
    }
}
export const Private: Story = {
    args: {
        access: Access.Private
    }
}
export const Protected: Story = {
    args: {
        access: Access.Protected
    }
}