

import type { Meta, StoryObj } from '@storybook/react';
import ActionMenu, {useChangeAccess, useDelete } from './ActionMenu';
//Things we have to import because of component dependencies
import { Access } from "../api"
import {Provider} from "react-redux"
import store from "../store"

/**
 * We have to wrap with a Provider because ActionMenu uses useTester
 * which directly depends on the redux state from keycloak
 */

const meta = {
    title: "components/ActionMenu",
    component: ActionMenu,
    decorators: [
        //{auth: roles: ["tester", "ow-tester"]
        (Story) => (<Provider store={store}><Story/></Provider>),
    ],
    parameters: {
        layout: 'centered',
    },
    tags: ['autodocs'],
    argTypes: {

    }
} satisfies Meta<typeof ActionMenu>;
export default meta;
type Story = StoryObj<typeof meta>;

export const ChangeAccess: Story = {
    args: {
        id: 0,
        owner: "owner",
        access: Access.Public,
        description: "empty",
        items: [useChangeAccess({
            onAccessUpdate: (id, owner, access) => {return id},
        })]
    }
}

//Invalid Hook call (onDelete?) outside of react render
//Cannot read properties of null (reading 'useState')
//useDelete
// export const Delete: Story = {
//     args: {
//         id: 1,
//         owner: "owner",
//         access: Access.Public,
//         description: "empty",
//         items: [useDelete({
//             onDelete: (id) => {return new Promise((a,b)=>a)},
//         })]
//     }
// }