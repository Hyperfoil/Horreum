import type { Meta, StoryObj } from '@storybook/react';
import JsonPathDocsLink from './JsonPathDocsLink';
const meta = {
    title: "components/JsonPathDocsLink",
    component: JsonPathDocsLink,
    parameters: {
        layout: 'centered',
        docs: { },        
    },
    tags: ['autodocs'],
    argTypes: {}
} satisfies Meta<typeof JsonPathDocsLink>;
export default meta;
type Story = StoryObj<typeof meta>;

//this also uses alerting callback from useContext(AppContext)
export const Primary: Story = {
    args: {}
}
