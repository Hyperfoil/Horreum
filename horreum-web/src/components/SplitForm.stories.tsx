import type { Meta, StoryObj } from '@storybook/react';
import SplitForm from './SplitForm';
//needed to render
const meta = {
    title: "components/SplitForm",
    component: SplitForm,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    tags: ['autodocs'],
    argTypes: {

    }
} satisfies Meta<typeof SplitForm>;
export default meta;
type Story = StoryObj<typeof meta>;

//Teams
export const Empty: Story = {
    args: {        
        itemType: "Team",
        newItem: id=> ({id, name: "", exists: false}),
        canAddItem: true,
        addItemText: "Add new team",
        noItemTitle: "There are no teams",
        noItemText: "Horreum currently does not have any teams registered.",
        canDelete: true,
        confirmDelete: team=>true,
        // eslint-disable-next-line
        onDelete: team=>{},
        items: [],
        // eslint-disable-next-line
        onChange: ()=>{},
        // eslint-disable-next-line
        onSelected: (v)=>{},
        loading: false,
        children: [
            <>child element</>
        ]
    }
}
export const CanDelete: Story = {
    args: {
        ...Empty.args,
        items: [{id: 1, name: "one"}]
    }
}
export const CannotDelete: Story = {
    args: {        
        ...Empty.args,
        canDelete: false,
        items: [{id: 1, name: "one"}]
    }
}
