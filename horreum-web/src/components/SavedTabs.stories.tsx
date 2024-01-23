import type { Meta, StoryObj } from '@storybook/react';
import SavedTabs, {SavedTab} from './SavedTabs';
//needed to render :(
import { MemoryRouter } from "react-router";
const meta = {
    title: "components/SavedTabs",
    component: SavedTabs,
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
    argTypes: {}
} satisfies Meta<typeof SavedTabs>;
export default meta;
type Story = StoryObj<typeof meta>;
//Admin.tsx
export const CanSave: Story = {
    args: {
        children: [
            (<SavedTab
                title="Administrators"
                fragment="administrators"
                // eslint-disable-next-line
                onSave={()=>new Promise((resolve,reject)=>{})}
                // eslint-disable-next-line
                onReset={()=>new Promise((resolve,reject)=>{})}
                isModified={()=>false}
            >first tab</SavedTab>),
            (<SavedTab
                title="Teams"
                fragment="teams"
                // eslint-disable-next-line
                onSave={()=>new Promise((resolve,reject)=>{})}
                // eslint-disable-next-line
                onReset={()=>new Promise((resolve,reject)=>{})}
                isModified={()=>false}
            >second tab</SavedTab>)
        ]
    },
}
export const CannotSave: Story = {
    args: {
        canSave: false,
        children: [
            (<SavedTab
                title="Administrators"
                fragment="administrators"
                // eslint-disable-next-line
                onSave={()=>new Promise((resolve,reject)=>{})}
                // eslint-disable-next-line
                onReset={()=>new Promise((resolve,reject)=>{})}
                isModified={()=>false}
            >first tab</SavedTab>),
            (<SavedTab
                title="Teams"
                fragment="teams"
                // eslint-disable-next-line
                onSave={()=>new Promise((resolve,reject)=>{})}
                // eslint-disable-next-line
                onReset={()=>new Promise((resolve,reject)=>{})}
                isModified={()=>false}
            >second tab</SavedTab>)
        ]
    },
}
