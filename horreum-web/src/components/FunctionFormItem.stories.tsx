import type { Meta, StoryObj } from '@storybook/react';
import FunctionFormItem from './FunctionFormItem';

const meta = {
    title: "components/FunctionFormItem",
    component: FunctionFormItem,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    tags: ['autodocs'],
    argTypes: {

    }
} satisfies Meta<typeof FunctionFormItem>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Default: Story = {
    args: {        
        label: "label",
        helpText: "helpText",
        value: "value",
        readOnly: false,
        // eslint-disable-next-line        
        onChange: (str)=>{}
    }
}
export const ReadOnly: Story = {
    args: {        
        label: "label",
        helpText: "helpText",
        value: "()=>{...}",
        readOnly: true,
        // eslint-disable-next-line        
        onChange: (str)=>{}
    }
}
