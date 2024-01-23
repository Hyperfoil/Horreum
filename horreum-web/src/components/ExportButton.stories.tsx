
import type { Meta, StoryObj } from '@storybook/react';
import {useContext, useEffect, useState} from "react"

import ExportButton from './ExportButton';

const meta = {
    title: "components/ExportButton",
    component: ExportButton,
    parameters: {
        layout: 'centered',
        docs: { },
        
    },
    tags: ['autodocs'],
    argTypes: {

    }
} satisfies Meta<typeof ExportButton>;
export default meta;
type Story = StoryObj<typeof meta>;

//ERROR cannot render because depends on react useContext :( for alerting
// export const Default: Story = {
//     args: {
//         name: "name",
//         // eslint-disable-next-line
//         export: async ()=>async ()=>{}
//     },
// }
