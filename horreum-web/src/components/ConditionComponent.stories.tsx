import type { Meta, StoryObj } from '@storybook/react';
import ConditionComponent from './ConditionComponent';
import {ConditionComponentTypeEnum} from '../generated/models/ConditionComponent'
const meta = {
    title: "components/ConditionComponent",
    component: ConditionComponent,
    parameters: {
        // Optional parameter to center the component in the Canvas. More info: https://storybook.js.org/docs/configure/story-layout
        layout: 'centered',
    },
    // This component will have an automatically generated Autodocs entry: https://storybook.js.org/docs/writing-docs/autodocs
    tags: ['autodocs'],
    // More on argTypes: https://storybook.js.org/docs/api/argtypes
    argTypes: {
        
    },  
} satisfies Meta<typeof ConditionComponent>;
export default meta;
type Story = StoryObj<typeof meta>;

//ConditionalComponent.properties is incorrect about the properties
//TODO we cannot render these until the ConditionalComponent.ts interface has correct properties

// export const EnumTester: Story = {
//     args: {
//         value: 1,
//         isTester: true,
//         name: "name",
//         title: "title",
//         description: "description",
//         type: ConditionComponentTypeEnum.Enum,
//         properties: {
//             scale: 1, //LogSlider
//             discrete: true, //LogSlider
//             min: 1, //LogSlider
//             max: 1, //LogSlider
//             unit: 1, //LogSlider
//             options : { 

//             }
//         },
//     },
// }