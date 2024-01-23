
import type { Meta, StoryObj } from '@storybook/react';
import ErrorBadge from './ErrorBadge';

const meta = {
    title: "components/ErrorBadge",
    component: ErrorBadge,
    parameters: {
        layout: 'centered',
        docs: { },
        
    },
    tags: ['autodocs'],
    argTypes: {

    }
} satisfies Meta<typeof ErrorBadge>;
export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
    args: {
        children: "children"
    },
}
