require "ISUI/ISPanel"
require "ISUI/ISButton"
require "ISUI/ISRichTextPanel"
require "ISUI/ISProgressBar"

local SetupPanel = ISPanel:derive("PZOptPreloaderSetupPanel")
local activePanel
local dismissedThisSession = false
local prefix = "IGUI_PZOptPreloader_Setup_"

local function plainText(value)
    return tostring(value or ""):gsub("<", "["):gsub(">", "]"):gsub("\n", " <LINE> ")
end

function SetupPanel:new(bridge)
    local core = getCore()
    local width = math.min(840, core:getScreenWidth() - 40)
    local height = math.min(650, core:getScreenHeight() - 40)
    local panel = ISPanel.new(self, (core:getScreenWidth() - width) / 2,
        (core:getScreenHeight() - height) / 2, width, height)
    panel.bridge = bridge
    panel.backgroundColor = {r = 0.08, g = 0.08, b = 0.08, a = 0.98}
    panel.title = getText(prefix .. "Title")
    panel.targetPath = bridge.target()
    panel.updateMode = bridge.isUpdate()
    return panel
end

function SetupPanel:createChildren()
    ISPanel.createChildren(self)
    local fontHeight = getTextManager():getFontHeight(UIFont.Small)
    local buttonHeight = math.max(30, fontHeight + 12)
    local row = buttonHeight + 6
    local buttonsY = self.height - 16 - row * 5
    self.description = ISRichTextPanel:new(16, 46, self.width - 32, buttonsY - 92)
    self.description:initialise()
    self.description.autosetheight = false
    self.description.background = false
    self.description.clip = true
    self.description:addScrollBars()
    self:addChild(self.description)
    self.progressBar = ISProgressBar:new(16, buttonsY - 38, self.width - 32, 28, "", UIFont.Small)
    self.progressBar:initialise()
    self:addChild(self.progressBar)
    local function button(offset, key, callback)
        local control = ISButton:new(16, buttonsY + row * offset, self.width - 32, buttonHeight,
            getText(prefix .. key), self, callback)
        control:initialise()
        self:addChild(control)
        return control
    end
    self.disableButton = button(0, self.updateMode and "Later" or "Disable", SetupPanel.onPrimary)
    self.manualButton = button(1, "ManualButton", SetupPanel.onManual)
    self.autoButton = button(2, self.updateMode and "UpdateButton" or "AutoButton", SetupPanel.onAutomatic)
    self.keepButton = button(3, "KeepPrevious", SetupPanel.onKeepPrevious)
    self.closeButton = button(4, "Close", SetupPanel.onClose)
    self:refresh()
end

function SetupPanel:refresh()
    local bridge = self.bridge
    local state = bridge.state()
    local issue = bridge.issue()
    local busy = bridge.busy()
    local detail = bridge.detail()
    local canKeep = bridge.canKeepPrevious()
    local previousVersion = bridge.previousVersion()
    local signature = state .. "\n" .. issue .. "\n" .. detail .. "\n" .. tostring(self.manual) .. tostring(canKeep) .. previousVersion
    if signature ~= self.lastContent then
        self.lastContent = signature
        local key = state == "idle" and issue or state
        if state == "failed" and (issue == "noRelease" or issue == "rateLimit" or issue == "checksum" or issue == "busy") then
            key = issue
        end
        local text = plainText(getText(prefix .. key))
        if state ~= "complete" and state ~= "disabled" and state ~= "retained" and state ~= "rollback" then
            text = text .. " <BR> " .. plainText(getText(prefix .. (self.updateMode and "UpdateIntro" or "Intro")))
        end
        if self.updateMode then
            text = text .. " <BR> " .. plainText(getText(prefix .. "Versions", bridge.installedVersion(), bridge.offeredVersion()))
        end
        text = text .. " <BR> " .. plainText(getText(prefix .. "Target")) .. " <LINE> " .. plainText(self.targetPath)
        if canKeep then text = text .. " <BR> " .. plainText(getText(prefix .. "PreviousAvailable", previousVersion)) end
        if self.manual then
            text = text .. " <BR> " .. plainText(getText(prefix .. "Manual"))
        end
        if detail ~= "" and (state ~= "idle" or issue ~= "missing") then text = text .. " <BR> " .. plainText(detail) end
        self.description.text = text
        self.description:paginate()
    end
    local completed, total = bridge.completed(), bridge.total()
    local fraction = total > 0 and math.min(1, completed / total) or 0
    self.progressBar:setProgress(fraction)
    local progressText = ""
    if total > 0 then
        if state == "download" or state == "verify" then
            progressText = string.format("%d%%  (%.1f / %.1f MB)", fraction * 100, completed / 1048576, total / 1048576)
        else
            progressText = string.format("%d%%  (%d / %d)", fraction * 100, completed, total)
        end
    end
    self.progressBar:setText(progressText)
    self.disableButton:setEnable(state ~= "disabled" and not (self.updateMode and state == "publish"))
    if self.updateMode then
        local title = getText(prefix .. (busy and "CancelUpdate" or "Later"))
        if title ~= self.disableButton.title then self.disableButton:setTitle(title) end
    end
    self.manualButton:setEnable(not busy and state ~= "disabled")
    self.autoButton:setEnable(not busy and state ~= "complete" and state ~= "disabled" and state ~= "retained" and state ~= "rollback")
    self.keepButton:setVisible(canKeep)
    self.keepButton:setEnable(canKeep and not busy)
    self.closeButton:setEnable(true)
    self.closeButton:setVisible(true)
end

function SetupPanel:update()
    ISPanel.update(self)
    -- Poll scalar job state at most ten times per second; controls and text layout are retained.
    local now = getTimestampMs()
    if not self.nextRefresh or now >= self.nextRefresh then
        self.nextRefresh = now + 100
        self:refresh()
    end
end

function SetupPanel:prerender()
    ISPanel.prerender(self)
    self:drawTextCentre(self.title, self.width / 2, 15, 1, 1, 1, 1, UIFont.Medium)
end

function SetupPanel:onDisable()
    self.bridge.disable()
    self:refresh()
end

function SetupPanel:onPrimary()
    if not self.updateMode then return self:onDisable() end
    if self.bridge.busy() then
        self.bridge.cancelDownload()
        self:refresh()
    else
        self:onClose()
    end
end

function SetupPanel:onManual()
    self.manual = true
    self.bridge.openReleases()
    self:refresh()
end

function SetupPanel:onAutomatic()
    self.bridge.startDownload()
    self:refresh()
end

function SetupPanel:onKeepPrevious()
    self.bridge.keepPrevious()
    self:refresh()
end

function SetupPanel:onClose()
    dismissedThisSession = true
    self.bridge.dismiss()
    self:removeFromUIManager()
    activePanel = nil
end

local function showSetup()
    local bridge = PZOptPreloader and PZOptPreloader.Installation
    if not bridge or not bridge.needsSetup or not bridge.needsSetup() then return end
    if dismissedThisSession or activePanel then return end
    activePanel = SetupPanel:new(bridge)
    activePanel:initialise()
    activePanel:addToUIManager()
    activePanel:setCapture(false)
    activePanel:setAlwaysOnTop(true)
end

local wrappedMenuUpdate
local nextMenuCheck = 0
local function onMainMenuEnter()
    showSetup()
    -- The network check may finish after OnMainMenuEnter. Chain the real menu update once.
    if MainScreen and MainScreen.update and MainScreen.update ~= wrappedMenuUpdate then
        local previousUpdate = MainScreen.update
        wrappedMenuUpdate = function(self, ...)
            local result = previousUpdate(self, ...)
            local now = getTimestampMs()
            if now >= nextMenuCheck and not self.inGame and self:getIsVisible() then
                nextMenuCheck = now + 500
                showSetup()
            end
            return result
        end
        MainScreen.update = wrappedMenuUpdate
    end
end

Events.OnMainMenuEnter.Add(onMainMenuEnter)
