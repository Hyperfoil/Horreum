import type { Meta, StoryObj } from '@storybook/react';
import NumberBound from './NumberBound';
//needed to render :(

const meta = {
    title: "components/NumberBound",
    component: NumberBound,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    tags: ['autodocs'],
    argTypes: {}
} satisfies Meta<typeof NumberBound>;
export default meta;
type Story = StoryObj<typeof meta>;

//does not work becuase it directly calls notificationsApi... so much to fix
export const EnabledInclusive: Story = {
    args: {
        enabled: true,
        inclusive: true,
        value: 10,
        // eslint-disable-next-line
        onChange: (method)=>{}
    }
}
export const EnabledExclusive: Story = {
    args: {
        enabled: true,
        inclusive: false,
        value: 10,
        // eslint-disable-next-line
        onChange: (method)=>{}
    }
}

export const NotEnabled: Story = {
    args: {
        enabled: false,
        inclusive: true,
        value: 10,
        // eslint-disable-next-line
        onChange: (method)=>{}
    }
}
export const EnabledDisabled: Story = {
    args: {
        enabled: true,
        isDisabled: true,
        inclusive: true,
        value: 10,
        // eslint-disable-next-line
        onChange: (method)=>{}
    }
}
export const NotEnabledDisabled: Story = {
    args: {
        enabled: false,
        isDisabled: true,
        inclusive: true,
        value: 10,
        // eslint-disable-next-line
        onChange: (method)=>{}
    }
}

