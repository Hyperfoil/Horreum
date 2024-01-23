import type { Meta, StoryObj } from '@storybook/react';
import ConfirmDeleteModal from './ConfirmDeleteModal';

const meta = {
    title: "components/ConfirmDeleteModal",
    component: ConfirmDeleteModal,
    parameters: {
        layout: 'centered',
    },
    tags: ['autodocs'],
    argTypes: {

    }
} satisfies Meta<typeof ConfirmDeleteModal>;
export default meta;
type Story = StoryObj<typeof meta>;

//Perhaps components should be in a modal, not defined as a modal to improve testability

export const Default: Story = {
    args: {
        isOpen: true,
        extra: "extra",
        description: "description",
        onDelete: ()=>new Promise((a,b)=>a)
    }
}



