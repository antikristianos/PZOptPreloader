-- WIP-only visual fixture. Never replaces the live Java installation bridge.
require "PZOptPreloader/Setup"

local previewPanel
local previewButton
local function openPreview()
    if previewPanel then previewPanel:removeFromUIManager() end
    local state, issue, keep = "idle", "update", false
    local bridge = {
        target = function() return "[UI TEST]" end,
        isUpdate = function() return true end,
        state = function() return state end,
        issue = function() return issue end,
        busy = function() return false end,
        detail = function() return "[UI TEST]" end,
        canKeepPrevious = function() return keep end,
        previousVersion = function() return "[UI TEST] 154126d0" end,
        installedVersion = function() return "[UI TEST] 154126d0" end,
        compatibleVersion = function() return "[UI TEST] b42.22.2-20261111-2036-fad57862" end,
        unverifiedVersion = function() return "[UI TEST] b42.22.2-20991231-2359-ffffffff" end,
        completed = function() return 0 end,
        total = function() return 0 end,
        startDownload = function() state = "complete" end,
        startUnverifiedDownload = function() state, issue, keep = "failed", "incompatible", true end,
        keepPrevious = function() state, keep = "retained", false end,
        openReleases = function() end,
        disable = function() end,
        cancelDownload = function() end,
        dismiss = function() end
    }
    previewPanel = PZOptPreloader.openWipUpdatePreview(bridge)
end

local function onMainMenuEnter()
    if previewButton then previewButton:removeFromUIManager() end
    local fontHeight = getTextManager():getFontHeight(UIFont.Small)
    local title = getText("IGUI_PZOptPreloader_Setup_Title") .. " [UI TEST]"
    local width = math.min(getCore():getScreenWidth() - 32,
        math.max(320, getTextManager():MeasureStringX(UIFont.Small, title) + 32))
    previewButton = ISButton:new(getCore():getScreenWidth() - width - 16, 16,
        width, math.max(30, fontHeight + 12), title, nil, openPreview)
    previewButton:initialise()
    previewButton:addToUIManager()
end

Events.OnMainMenuEnter.Add(onMainMenuEnter)
