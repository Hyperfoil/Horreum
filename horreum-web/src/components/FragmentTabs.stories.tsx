import type { Meta, StoryObj } from '@storybook/react';
import FragmentTabs, {FragmentTab} from './FragmentTabs';
//needed to render
import { MemoryRouter } from "react-router";
const meta = {
    title: "components/FragmentTabs",
    component: FragmentTabs,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    decorators: [
        //Memory router because it has useHistory
        (Story) => (
            
            <MemoryRouter initialEntries={['/']}>
                
                    <Story/>
            
            </MemoryRouter>),
    ],
    tags: ['autodocs'],
    argTypes: {

    }
} satisfies Meta<typeof FragmentTabs>;
export default meta;
type Story = StoryObj<typeof meta>;

export const CanCreate: Story = {
    
    args: {        
        tabIndexRef: {current: 0},
        // eslint-disable-next-line
        navigate: async (current,next)=>{},
        children: [
            <FragmentTab title="firstTitle" fragment="firstFragment">first fragment</FragmentTab>,
            <FragmentTab title="secondTitle" fragment="secondFragment">second fragment</FragmentTab>,
        ]
    }
}
