import type { Meta, StoryObj } from '@storybook/react';
import SaveChangesModal from './SaveChangesModal';
//needed to render :(
const meta = {
    title: "components/SaveChangesModal",
    component: SaveChangesModal,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    tags: ['autodocs'],
    argTypes: {}
} satisfies Meta<typeof SaveChangesModal>;
export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
    args: {
        isOpen: true,
        // eslint-disable-next-line
        onClose: ()=>{},
        // eslint-disable-next-line
        onReset: ()=>{},
    },
}
