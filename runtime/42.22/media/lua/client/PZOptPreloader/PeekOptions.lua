if not PZOptPeek or not PZOptPeek.isActive() then return end
local options = PZAPI.ModOptions:create("PZPeek", "PZOpt / Peek a View")
-- MainOptions translates slider labels itself. Keep keys here to avoid a second
-- formatting pass over an already translated string containing percentages.
local visibility = options:addSlider("visibility", "IGUI_PZPeek_Visibility", 0, 100, 1, 30,
    "IGUI_PZPeek_VisibilityTooltip")
options:addDescription("IGUI_PZPeek_VisibilityHelp")
local visibilityHelp = options.data[#options.data]

-- Keep the original PZPeek.visibility storage entry. Only the temporary menu
-- view places its existing control inside PeekAView's tree section.
local function menuData()
    local peekOptions = PZAPI.ModOptions:getOptions("PeekAView")
    if not peekOptions or peekOptions:getOption("visibility") then return nil end
    local range = peekOptions:getOption("treeFadeRevealRadius")
    if not range then return nil end
    local insertAt
    for index, option in ipairs(peekOptions.data) do
        local help = peekOptions.data[index + 1]
        if option == range and help and help.type == "description"
                and help.text == getText("UI_PAV_TreeFadeRevealRadiusDescription") then
            insertAt = index + 2
            break
        end
    end
    if not insertAt then return nil end
    local peekData = {}
    for index, option in ipairs(peekOptions.data) do
        if index == insertAt then
            table.insert(peekData, visibility)
            table.insert(peekData, visibilityHelp)
        end
        table.insert(peekData, option)
    end
    if insertAt > #peekOptions.data then
        table.insert(peekData, visibility)
        table.insert(peekData, visibilityHelp)
    end
    local peekView = setmetatable({ data = peekData }, { __index = peekOptions })
    local data = {}
    for _, group in ipairs(PZAPI.ModOptions.Data) do
        if group == peekOptions then
            table.insert(data, peekView)
        elseif group ~= options then
            table.insert(data, group)
        end
    end
    return data
end

local function finishMenu(previousData, ok, ...)
    PZAPI.ModOptions.Data = previousData
    if not ok then error((...)) end
    return ...
end

local menuInstalled = false
local function installMenu()
    if menuInstalled or not MainOptions or not MainOptions.addModOptionsPanel then return end
    local previous = MainOptions.addModOptionsPanel
    function MainOptions:addModOptionsPanel(...)
        local data = menuData()
        if not data then return previous(self, ...) end
        local previousData = PZAPI.ModOptions.Data
        PZAPI.ModOptions.Data = data
        return finishMenu(previousData, pcall(previous, self, ...))
    end
    menuInstalled = true
end

local function applyVisibility()
    if PZOptPeek and PZOptPeek.setVisibility then
        PZOptPeek.setVisibility(visibility:getValue())
    end
end

visibility.onChangeApply = function(self, value)
    if PZOptPeek and PZOptPeek.setVisibility then PZOptPeek.setVisibility(value) end
end
Events.OnGameBoot.Add(applyVisibility)
Events.OnGameBoot.Add(installMenu)
installMenu()
