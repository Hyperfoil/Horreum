import type { Meta, StoryObj } from '@storybook/react';
import OwnerAccess from './OwnerAccess';
//needed to render :(
import {Access} from '../api';
const meta = {
    title: "components/OwnerAccess",
    component: OwnerAccess,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    tags: ['autodocs'],
    argTypes: {}
} satisfies Meta<typeof OwnerAccess>;
export default meta;
type Story = StoryObj<typeof meta>;

export const Public: Story = {
    args: {
        owner: "owner",
        access: Access.Public,
        // eslint-disable-next-line
        onUpdate: (owner,access)=>{},
        readOnly: false,
    }
}
export const Protected: Story = {
    args: {
        owner: "owner",
        access: Access.Protected,
        // eslint-disable-next-line
        onUpdate: (owner,access)=>{},
        readOnly: false,
    }
}
export const Private: Story = {
    args: {
        owner: "owner",
        access: Access.Private,
        // eslint-disable-next-line
        onUpdate: (owner,access)=>{},
        readOnly: false,
    }
}
export const ReadOnly: Story = {
    args: {
        owner: "owner",
        access: Access.Public,
        // eslint-disable-next-line
        onUpdate: (owner,access)=>{},
        readOnly: true,
    }
}