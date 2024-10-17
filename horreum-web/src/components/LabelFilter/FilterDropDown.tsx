import React from "react";
import {Menu, MenuContent, MenuItem, MenuList, MenuToggle, Popper} from "@patternfly/react-core";
import FilterIcon from "@patternfly/react-icons/dist/esm/icons/filter-icon";


interface FilterDropDownProps {
    options: string[];
    activeAttributeMenu: string;
    setActiveAttributeMenu(val: string): any;
}

export default function FilterDropDown(props: FilterDropDownProps) {
    const [isAttributeMenuOpen, setIsAttributeMenuOpen] = React.useState(false);
    const attributeToggleRef = React.useRef<HTMLButtonElement>(null);
    const attributeMenuRef = React.useRef<HTMLDivElement>(null);
    const attributeContainerRef = React.useRef<HTMLDivElement>(null);

    const handleAttribueMenuKeys = (event: KeyboardEvent) => {
        if (!isAttributeMenuOpen) {
            return;
        }
        if (
            attributeMenuRef.current?.contains(event.target as Node) ||
            attributeToggleRef.current?.contains(event.target as Node)
        ) {
            if (event.key === 'Escape' || event.key === 'Tab') {
                setIsAttributeMenuOpen(!isAttributeMenuOpen);
                attributeToggleRef.current?.focus();
            }
        }
    };

    const handleAttributeClickOutside = (event: MouseEvent) => {
        if (isAttributeMenuOpen && !attributeMenuRef.current?.contains(event.target as Node)) {
            setIsAttributeMenuOpen(false);
        }
    };

    React.useEffect(() => {
        window.addEventListener('keydown', handleAttribueMenuKeys);
        window.addEventListener('click', handleAttributeClickOutside);
        return () => {
            window.removeEventListener('keydown', handleAttribueMenuKeys);
            window.removeEventListener('click', handleAttributeClickOutside);
        };
    }, [isAttributeMenuOpen, attributeMenuRef]);

    const onAttributeToggleClick = (ev: React.MouseEvent) => {
        ev.stopPropagation(); // Stop handleClickOutside from handling
        setTimeout(() => {
            if (attributeMenuRef.current) {
                const firstElement = attributeMenuRef.current.querySelector('li > button:not(:disabled)');
                firstElement && (firstElement as HTMLElement).focus();
            }
        }, 0);
        setIsAttributeMenuOpen(!isAttributeMenuOpen);
    };

    const attributeToggle = (
        <MenuToggle
            ref={attributeToggleRef}
            onClick={onAttributeToggleClick}
            isExpanded={isAttributeMenuOpen}
            icon={<FilterIcon />}
        >
            {props.activeAttributeMenu}
        </MenuToggle>
    );
    const attributeMenu = (
        // eslint-disable-next-line no-console
        <Menu
            ref={attributeMenuRef}
            onSelect={(_ev, itemId) => {
                props.setActiveAttributeMenu(itemId?.toString() as string);
                setIsAttributeMenuOpen(!isAttributeMenuOpen);
            }}
        >
            <MenuContent>
                <MenuList>
                    {props.options.map((option, idx) => <MenuItem itemId={option} key={option + idx}>{option}</MenuItem>)}
                </MenuList>
            </MenuContent>
        </Menu>
    );

    return (
        <div ref={attributeContainerRef}>
            <Popper
                trigger={attributeToggle}
                triggerRef={attributeToggleRef}
                popper={attributeMenu}
                popperRef={attributeMenuRef}
                appendTo={attributeContainerRef.current || undefined}
                isVisible={isAttributeMenuOpen}
            />
        </div>
    );

}