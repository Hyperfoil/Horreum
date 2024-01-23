import type { Meta, StoryObj } from '@storybook/react';
import HttpActionUrlSelector from './HttpActionUrlSelector';
const meta = {
    title: "components/HttpActionUrlSelector",
    component: HttpActionUrlSelector,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    tags: ['autodocs'],
    argTypes: {}
} satisfies Meta<typeof HttpActionUrlSelector>;
export default meta;
type Story = StoryObj<typeof meta>;

//this also uses alerting callback from useContext(AppContext)
export const Default: Story = {
    args: {
        active: true,
        value: "",
        // eslint-disable-next-line
        setValue: (v)=>{},
        isDisabled: false,
        isReadOnly: false,
        // eslint-disable-next-line
        setValid: (v)=>{},
        extraCheck: (v)=>true
    }
}
