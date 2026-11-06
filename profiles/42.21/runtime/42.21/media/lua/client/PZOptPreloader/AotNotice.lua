require "ISUI/ISModalDialog"

local function showAotSetupNotice()
    local bridge = PZOptPreloader and PZOptPreloader.AOT
    if not bridge or not bridge.takeSetupNotice then return end
    if bridge.takeSetupNotice() ~= "initial" then return end
    local key = "IGUI_PZOptPreloader_AotSetupInfo"
    local core = getCore()
    local width, height = 560, 220
    local modal = ISModalDialog:new(1, 1, width, height, getText(key), false, nil, nil)
    modal:initialise()
    modal:setX((core:getScreenWidth() - modal:getWidth()) / 2)
    modal:setY((core:getScreenHeight() - modal:getHeight()) / 2)
    modal:setCapture(true)
    modal:setAlwaysOnTop(true)
    modal:addToUIManager()
end

Events.OnMainMenuEnter.Add(showAotSetupNotice)
