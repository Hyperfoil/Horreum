import type { Meta, StoryObj } from '@storybook/react';
import NotificationMethodSelect from './NotificationMethodSelect';
//needed to render :(

const meta = {
    title: "components/NotificationMethodSelect",
    component: NotificationMethodSelect,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    tags: ['autodocs'],
    argTypes: {}
} satisfies Meta<typeof NotificationMethodSelect>;
export default meta;
type Story = StoryObj<typeof meta>;

//does not work becuase it directly calls notificationsApi... so much to fix
export const Default: Story = {
    args: {
        isDisabled: false,
        method: "method",
        // eslint-disable-next-line
        onChange: (method)=>{}
    }
}
