import type { Meta, StoryObj } from '@storybook/react';
import HelpPopover from './HelpPopover';
const meta = {
    title: "components/HelpPopover",
    component: HelpPopover,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    tags: ['autodocs'],
    argTypes: {}
} satisfies Meta<typeof HelpPopover>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Default: Story = {
    args: {
        header: "header",
        text: "popover text"
    }
}
