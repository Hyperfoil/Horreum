import type { Meta, StoryObj } from '@storybook/react';
import OptionalFunction from './OptionalFunction';
//needed to render :(

const meta = {
    title: "components/OptionalFunction",
    component: OptionalFunction,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    tags: ['autodocs'],
    argTypes: {}
} satisfies Meta<typeof OptionalFunction>;
export default meta;
type Story = StoryObj<typeof meta>;

//does not work becuase it directly calls notificationsApi... so much to fix
export const Default: Story = {
    args: {
        func: "func",
        // eslint-disable-next-line
        onChange: (method)=>{},
        readOnly: false,
        undefinedText: "undefinedText",
        addText: "addText",
        defaultFunc: "defaultFunc"
    }
}
export const NullFunc: Story = {
    args: {
        func: null,
        // eslint-disable-next-line
        onChange: (method)=>{},
        readOnly: false,
        undefinedText: "undefinedText",
        addText: "addText",
        defaultFunc: "defaultFunc"
    }    
}
export const NullFuncReadOnly: Story = {
    args: {
        func: null,
        // eslint-disable-next-line
        onChange: (method)=>{},
        readOnly: true,
        undefinedText: "undefinedText",
        addText: "addText",
        defaultFunc: "defaultFunc"
    }    
}