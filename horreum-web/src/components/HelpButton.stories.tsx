import type { Meta, StoryObj } from '@storybook/react';
import HelpButton from './HelpButton';
const meta = {
    title: "components/HelpButton",
    component: HelpButton,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    tags: ['autodocs'],
    argTypes: {

    }
} satisfies Meta<typeof HelpButton>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Default: Story = {
    args: {}
}
