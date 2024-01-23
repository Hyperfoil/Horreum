import type { Meta, StoryObj } from '@storybook/react';

import ShareLinkModal from "./ShareLinkModal";

// More on how to set up stories at: https://storybook.js.org/docs/writing-stories#default-export
const meta = {
    title: "components/ShareLinkModal",
    component: ShareLinkModal,
    parameters: {
        // Optional parameter to center the component in the Canvas. More info: https://storybook.js.org/docs/configure/story-layout
        layout: 'centered',
    },
    // This component will have an automatically generated Autodocs entry: https://storybook.js.org/docs/writing-docs/autodocs
    tags: ['autodocs'],
    // More on argTypes: https://storybook.js.org/docs/api/argtypes
    argTypes: {
        
    },  
} satisfies Meta<typeof ShareLinkModal>;
export default meta;
type Story = StoryObj<typeof meta>;

/**
 * <ShareLinkModal
                        key="link"
                        isOpen={shareLinkModalOpen}
                        onClose={() => setShareLinkModalOpen(false)}
                        isTester={isTester}
                        link={config.token ? config.tokenToLink(props.id, config.token) : ""}
                        onReset={() => config.onTokenReset(props.id)}
                        onDrop={() => config.onTokenDrop(props.id)}
 */

export const Open: Story = {
    args: {
        isOpen: true,
        isTester: false,
        link: "link",
    },
}
export const Tester: Story = {
    args: {
        isOpen: true,
        isTester: true,
        link: "link",
    },
}