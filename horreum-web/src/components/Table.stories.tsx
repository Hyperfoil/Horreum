import type { Meta, StoryObj } from '@storybook/react';
import Table from './Table';
//needed to render
//const meta = {
const meta: Meta<typeof Table> = {
    title: "components/Table",
    component: Table,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    //args: {...Table.defaultProps},
    tags: ['autodocs'],
//} satisfies Meta<typeof Table>;
}
export default meta;
type Story = StoryObj<typeof meta>;


export const Empty: Story = {
    args: {        
        columns: [],
        data: [],
        sortBy: [],
        isLoading: false,
        // selected: {"foo":false,"bar":true},
        // eslint-disable-next-line
        // onSelected: (ids)=>{},
    }
}
//RunList
export const  Simple: Story = {
    args: {
        columns: [
            {
                Header: "alpha",
                
                accessor: "a"
            },
            {
                Header: "bravo",
                accessor: "b"
            }
        ],
        data: [
            {
                a: "apple",
                b: "banana"
            },
            {
                a: "ant",
                b: "bee"
            }
        ],
        sortBy: [],
        isLoading: false
    }
}