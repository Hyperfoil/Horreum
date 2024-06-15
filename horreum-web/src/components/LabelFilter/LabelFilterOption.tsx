import React from "react";
import {Menu, MenuContent, MenuItem, MenuList, MenuToggle, Popper} from "@patternfly/react-core";

interface FilterOptionProps {
    name: string;
    options: string[];
    filter: any;
    setFilter(filter: any): any;
    selection: string;
    setSelection(selection: string): any;
}


export default function LabelFilterOption(props: FilterOptionProps) {
    const [isMenuOpen, setIsMenuOpen] = React.useState<boolean>(false);
    const toggleRef = React.useRef<HTMLButtonElement>(null);
    const menuRef = React.useRef<HTMLDivElement>(null);
    const containerRef = React.useRef<HTMLDivElement>(null);

    const handleMenuKeys = (event: KeyboardEvent) => {
        if (isMenuOpen && menuRef.current?.contains(event.target as Node)) {
            if (event.key === 'Escape' || event.key === 'Tab') {
                setIsMenuOpen(!isMenuOpen);
                toggleRef.current?.focus();
            }
        }
    };

    const handleClickOutside = (event: MouseEvent) => {
        if (isMenuOpen && !menuRef.current?.contains(event.target as Node)) {
            setIsMenuOpen(false);
        }
    };

    React.useEffect(() => {
        window.addEventListener('keydown', handleMenuKeys);
        window.addEventListener('click', handleClickOutside);
        return () => {
            window.removeEventListener('keydown', handleMenuKeys);
            window.removeEventListener('click', handleClickOutside);
        };
    }, [isMenuOpen, menuRef]);

    const onToggleClick = (ev: React.MouseEvent) => {
        ev.stopPropagation(); // Stop handleClickOutside from handling
        setTimeout(() => {
            if (menuRef.current) {
                const firstElement = menuRef.current.querySelector('li > button:not(:disabled)');
                firstElement && (firstElement as HTMLElement).focus();
            }
        }, 0);
        setIsMenuOpen(!isMenuOpen);
    };

    function onSelect(event: React.MouseEvent | undefined, itemId: string | number | undefined) {
        if (typeof itemId === 'undefined') {
            return;
        }

        props.setSelection(itemId.toString());
        const newFilter = {...props.filter};
        newFilter[props.name] = itemId;
        props.setFilter(newFilter);
        setIsMenuOpen(!isMenuOpen);
    }

    const toggle = (
        <MenuToggle
            ref={toggleRef}
            onClick={onToggleClick}
            isExpanded={isMenuOpen}
            style={
                {
                    width: '200px'
                } as React.CSSProperties
            }
        >
            Filter by {props.name}
        </MenuToggle>
    );

    const menu = (
        <Menu ref={menuRef} id="attribute-search-{props.name}-menu" onSelect={onSelect} selected={props.selection}>
            <MenuContent>
                <MenuList>
                    {
                        props.options.map((option) => (
                            <MenuItem isSelected={props.selection === option} itemId={option} key={option}>
                                {option}
                            </MenuItem>
                        ))
                    }
                </MenuList>
            </MenuContent>
        </Menu>
    );

    return (
        <div ref={containerRef}>
            <Popper
                trigger={toggle}
                triggerRef={toggleRef}
                popper={menu}
                popperRef={menuRef}
                appendTo={containerRef.current || undefined}
                isVisible={isMenuOpen}
            />
        </div>
    );

}