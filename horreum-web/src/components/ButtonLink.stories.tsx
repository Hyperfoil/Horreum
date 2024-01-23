import type { Meta, StoryObj } from '@storybook/react';
import ButtonLink from './ButtonLink';
//Things we have to import because of component dependencies
import {Router} from "react-router-dom"
import {createBrowserHistory} from "history";

const meta = {
    title: "components/ButtonLink",
    component: ButtonLink,
    parameters: {
        // Optional parameter to center the component in the Canvas. More info: https://storybook.js.org/docs/configure/story-layout
        layout: 'centered',
    },
    decorators: [
        (Story) => (<Router history={createBrowserHistory()}><Story/></Router>),
    ],
    // This component will have an automatically generated Autodocs entry: https://storybook.js.org/docs/writing-docs/autodocs
    tags: ['autodocs'],
    // More on argTypes: https://storybook.js.org/docs/api/argtypes
    argTypes: {
        
    },  
} satisfies Meta<typeof ButtonLink>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Primary: Story = {
    args: {
        variant: "primary",
        to: "link",
        isDisabled: false,
        children: [<>buttonLink</>]
    },
}
export const Secondary: Story = {
    args: {
        variant: "secondary",
        to: "link",
        isDisabled: false,
        children: [<>buttonLink</>]
    },
}
export const Tertiary: Story = {
    args: {
        variant: "tertiary",
        to: "link",
        isDisabled: false,
        children: [<>buttonLink</>]
    },
}
export const Control: Story = {
    args: {
        variant: "control",
        to: "link",
        isDisabled: false,
        children: [<>buttonLink</>]
    },
}
export const Disabled: Story = {
    args: {
        variant: "primary",
        to: "link",
        isDisabled: true,
        children: [<>buttonLink</>]
    },
}
