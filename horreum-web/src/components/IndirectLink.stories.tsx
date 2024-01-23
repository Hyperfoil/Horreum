import type { Meta, StoryObj } from '@storybook/react';
import IndirectLink from './IndirectLink';
const meta = {
    title: "components/IndirectLink",
    component: IndirectLink,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    tags: ['autodocs'],
    argTypes: {}
} satisfies Meta<typeof IndirectLink>;
export default meta;
type Story = StoryObj<typeof meta>;

export const Primary: Story = {
    args: {
        variant: "primary",
        // eslint-disable-next-line
        onNavigate: async ()=>"str",
        children: [
            (<>child</>)
        ]
    }
}
