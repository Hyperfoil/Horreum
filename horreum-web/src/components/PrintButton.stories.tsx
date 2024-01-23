import type { Meta, StoryObj } from '@storybook/react';
import PrintButton from './PrintButton';
//needed to render :(
import {useRef} from "react"
const meta = {
    title: "components/PrintButton",
    component: PrintButton,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    tags: ['autodocs'],
    argTypes: {}
} satisfies Meta<typeof PrintButton>;
export default meta;
type Story = StoryObj<typeof meta>;

//does not work becuase it directly calls notificationsApi... so much to fix
export const Default: Story = {
    args: {
        //placeholder because othewise it complains about the typescript type
        printRef: (null as unknown as React.RefObject<HTMLDivElement>),
    },
    render: function Render(args){
        const ref = useRef(null);
        return (<PrintButton {...args} printRef={ref}/>)
    }
}
