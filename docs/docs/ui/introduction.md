---
title: Introduction
sidebar_label: Introduction
---

import ButtonBoxList from "@site/src/components/ButtonBoxList";
import ButtonBox from "@site/src/components/ButtonBox";
import GearSVG from "@site/static/icons/gear.svg";
import DesktopComputerSVG from "@site/static/icons/desktop-computer.svg";
import LabelSVG from "@site/static/icons/label.svg";
import UsersSVG from "@site/static/icons/users.svg";
import ComponentsSVG from "@site/static/icons/information-architecture.svg";

Not every message can be handled by code, this is why Airy comes with different UIs ready for you and your teams to use.

While the [Chat Plugin](sources/chatplugin/overview.md) is the open-source chat UI for your website and app visitors, Airy UI has different all the UI interfaces you need internally for a messaging platform.

Airy UI comes with an open-source, customizable [inbox](inbox), filled with the conversations of all your [sources](sources/introduction.md),
Additional features like [Filters, Search](inbox) and [Tags](tags) help you.

<ButtonBoxList>
    <ButtonBox
        icon={<GearSVG />}
        iconInvertible={true}
        title='UI Quickstart'
        description='Step by Step Guide on getting up and running with the UI'
        link='ui/quickstart'
    />
    <ButtonBox
        icon={<DesktopComputerSVG />}
        iconInvertible={true}
        title='Inbox'
        description='One inbox to see all your conversations & respond to them'
        link='ui/inbox'
    />
    <ButtonBox
        icon={<LabelSVG />}
        iconInvertible={true}
        title='Tags'
        description='Tag your conversations for easy filtering, searching & segmenting'
        link='ui/tags'
    />    
    <ButtonBox
        icon={<ComponentsSVG />}
        iconInvertible={true}
        title='UI Components'
        description='Buttons, Inputs, Loaders and all Airy UI Components '
        link='ui/components'
    />
</ButtonBoxList>