
import type { Meta, StoryObj } from '@storybook/react';
import ExportImport from './ExportImport';

const meta = {
    title: "components/ExportImport",
    component: ExportImport,
    parameters: {
        layout: 'centered',
        docs: { },
        
    },
    tags: ['autodocs'],
    argTypes: {

    }
} satisfies Meta<typeof ExportImport>;
export default meta;
type Story = StoryObj<typeof meta>;

//ERROR cannot render because depends on react useContext :( for alerting
// export const Default: Story = {
//     args: {
//         name: "name",
//         // eslint-disable-next-line
//         export: async ()=>async ()=>{},
//         // eslint-disable-next-line
//         import: async(cfg: string)=>{},
//         // eslint-disable-next-line
//         validate: async(cfg: string)=>true,
//     },
// }
