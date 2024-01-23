
import type { Meta, StoryObj } from '@storybook/react';
import EnumSelect from './EnumSelect';

const meta = {
    title: "components/EnumSelect",
    component: EnumSelect,
    parameters: {
        layout: 'centered',
        docs: { },
        
    },
    tags: ['autodocs'],
    argTypes: {

    }
} satisfies Meta<typeof EnumSelect>;
export default meta;
type Story = StoryObj<typeof meta>;

//TODO stories size is rendering too small for drop down
export const Enabled: Story = {
    args: {
        isDisabled: false,
        // eslint-disable-next-line
        onSelect: (v)=>{},
        selected: "uno",
        options: {
            "uno": <>uno</>,
            "dos": <>dos</>
        }
    },
}
export const Disabled: Story = {
    args: {
        isDisabled: true,
        // eslint-disable-next-line
        onSelect: (v)=>{},
        selected: "uno",
        options: {
            "uno": <>uno</>,
            "dos": <>dos</>
        }
    },
}