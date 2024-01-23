import type { Meta, StoryObj } from '@storybook/react';
import Labels from './Labels';
const meta = {
    title: "components/Labels",
    component: Labels,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    tags: ['autodocs'],
    argTypes: {}
} satisfies Meta<typeof Labels>;
export default meta;
type Story = StoryObj<typeof meta>;

//this also uses alerting callback from useContext(AppContext)
export const Default: Story = {
    args: {
        labels: ["labelOne","labelTwo"],
        // eslint-disable-next-line
        onChange: (labels)=>{},
        isReadOnly: false,
        defaultMetrics: true,
        defaultFiltering: true,
    }
}
