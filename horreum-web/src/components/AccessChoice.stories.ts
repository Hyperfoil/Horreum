import type { Meta, StoryObj } from '@storybook/react';

import AccessChoice from './AccessChoice';
import { Access } from "../api"

const meta = {
    title: "components/AccessChoice",
    component: AccessChoice,
    parameters: {
        layout: 'centered',
    },
    tags: ['autodocs'],
    argTypes: {

    }
} satisfies Meta<typeof AccessChoice>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Public: Story = {
    args: {
        checkedValue: Access.Public
    }
}
export const Private: Story = {
    args: {
        checkedValue: Access.Private
    }
}
export const Protected: Story = {
    args: {
        checkedValue: Access.Protected
    }
}
