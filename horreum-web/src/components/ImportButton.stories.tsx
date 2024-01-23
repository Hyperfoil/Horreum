import type { Meta, StoryObj } from '@storybook/react';
import ImportButton from './ImportButton';
const meta = {
    title: "components/ImportButton",
    component: ImportButton,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    tags: ['autodocs'],
    argTypes: {}
} satisfies Meta<typeof ImportButton>;
export default meta;
type Story = StoryObj<typeof meta>;

//this also uses alerting callback from useContext(AppContext)
export const Default: Story = {
    args: {
        label: "foo",
        // eslint-disable-next-line
        onLoad: async (config)=>{},
        // eslint-disable-next-line
        onImport: async (config)=>{},
        // eslint-disable-next-line
        onImported: ()=>{} 
    }
}
