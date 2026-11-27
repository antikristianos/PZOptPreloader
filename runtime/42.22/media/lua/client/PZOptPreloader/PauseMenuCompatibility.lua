-- Final layout after both native menu extensions have applied their own positions.
local function compatible(menu)
    return menu.inGame and menu.bottomPanel and menu.optionsOption
        and menu.pzoptMenuOption and menu.pzoptMenuOption.internal == "PZOPT_MENU"
        and menu.modOptionsOption and menu.modOptionsOption.internal == "MODOPTIONS"
        and menu.pzoptMenuOption:isVisible() and menu.modOptionsOption:isVisible()
end

local function place(label, y)
    if label:getY() ~= y then label:setY(y) end
    return label:getBottom()
end

local function finishLayout(menu, ...)
    if compatible(menu) then
        local bottom = place(menu.pzoptMenuOption, menu.optionsOption:getBottom())
        bottom = place(menu.modOptionsOption, bottom)
        if menu.sb_options and menu.sb_options:isVisible() then
            bottom = place(menu.sb_options, bottom + 16)
        end
        if menu.exitOption and menu.exitOption:isVisible() then
            bottom = place(menu.exitOption, bottom + 16)
        end
        if menu.quitToDesktopOption and menu.quitToDesktopOption:isVisible() then
            bottom = place(menu.quitToDesktopOption, bottom + (isClient() and 0 or 16))
        end
        if menu.bottomPanel:getHeight() ~= bottom then menu.bottomPanel:setHeight(bottom) end
    end
    return ...
end

local function finishFocus(menu, ...)
    if compatible(menu) and menu.joypadButtonsY then
        local rows = menu.joypadButtonsY
        local selected = menu.joypadButtons or rows[menu.joypadIndexY or 0]
        local optimization, configs
        for index, row in ipairs(rows) do
            if row[1] == menu.pzoptMenuOption then optimization = index end
            if row[1] == menu.modOptionsOption then configs = index end
        end
        if optimization and configs and configs ~= optimization + 1 then
            local row = table.remove(rows, configs)
            if configs < optimization then optimization = optimization - 1 end
            table.insert(rows, optimization + 1, row)
        end
        if selected then
            for index, candidate in ipairs(rows) do
                if candidate == selected then
                    menu.joypadIndexY = index
                    menu.joypadButtons = selected
                    break
                end
            end
        end
    end
    return ...
end

local function install()
    if not MainScreen then return end
    if MainScreen.render and MainScreen.render ~= MainScreen.pzoptPreloaderPauseMenuRender then
        local previous = MainScreen.render
        local wrapper = function(self, ...) return finishLayout(self, previous(self, ...)) end
        MainScreen.pzoptPreloaderPauseMenuRender = wrapper
        MainScreen.render = wrapper
    end
    if MainScreen.onGainJoypadFocus and MainScreen.onGainJoypadFocus ~= MainScreen.pzoptPreloaderPauseMenuFocus then
        local previous = MainScreen.onGainJoypadFocus
        local wrapper = function(self, ...) return finishFocus(self, previous(self, ...)) end
        MainScreen.pzoptPreloaderPauseMenuFocus = wrapper
        MainScreen.onGainJoypadFocus = wrapper
    end
end

Events.OnGameBoot.Add(install)
Events.OnMainMenuEnter.Add(install)
Events.OnGameStart.Add(install)
