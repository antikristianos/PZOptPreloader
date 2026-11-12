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
    local width = math.floor(core:getScreenWidth() * 0.5)
    local height = math.floor(core:getScreenHeight() * 0.5)
    local panel = ISPanel.new(self, (core:getScreenWidth() - width) / 2,
        (core:getScreenHeight() - height) / 2, width, height)
    panel.bridge = bridge
    panel.backgroundColor = {r = 0.08, g = 0.08, b = 0.08, a = 0.98}
    panel.title = getText(prefix .. "Title")
    panel.targetPath = bridge.target()
    panel.updateMode = bridge.isUpdate()
    panel.screenWidth = core:getScreenWidth()
    panel.screenHeight = core:getScreenHeight()
    return panel
end

function SetupPanel:createChildren()
    ISPanel.createChildren(self)
    local fontHeight = getTextManager():getFontHeight(UIFont.Small)
    local buttonHeight = math.max(30, fontHeight + 12)
    self.buttonHeight = buttonHeight
    self.smallFontHeight = fontHeight
    local textTop = math.max(46, 15 + getTextManager():getFontHeight(UIFont.Medium) + 12)
    self.description = ISRichTextPanel:new(16, textTop, self.width - 32, 32)
    self.description:initialise()
    self.description.autosetheight = false
    self.description.background = false
    self.description.clip = true
    self.description:addScrollBars()
    self:addChild(self.description)
    self.progressBar = ISProgressBar:new(16, 0, self.width - 32, math.max(28, fontHeight + 4), "", UIFont.Small)
    self.progressBar:initialise()
    self:addChild(self.progressBar)
    local function button(key, callback)
        local control = ISButton:new(16, 0, self.width - 32, buttonHeight,
            getText(prefix .. key), self, callback)
        control:initialise()
        self:addChild(control)
        return control
    end
    self.disableButton = button(self.updateMode and "Later" or "Disable", SetupPanel.onPrimary)
    self.manualButton = button("ManualButton", SetupPanel.onManual)
    self.autoButton = button(self.updateMode and "UpdateButton" or "AutoButton", SetupPanel.onAutomatic)
    self.unverifiedButton = button("TryLatest", SetupPanel.onUnverified)
    self.keepButton = button("KeepPrevious", SetupPanel.onKeepPrevious)
    self.closeButton = button("Close", SetupPanel.onClose)
    self:refresh()
end

function SetupPanel:layoutControls()
    local key = self.screenWidth .. ":" .. self.screenHeight .. self.title
        .. tostring(self.unverifiedButton:getIsVisible()) .. tostring(self.keepButton:getIsVisible())
        .. self.autoButton.title .. self.manualButton.title .. self.disableButton.title
        .. self.keepButton.title .. self.unverifiedButton.title .. self.closeButton.title
    if key == self.lastLayout then return end
    self.lastLayout = key
    local padding, gap = 16, 6
    local width = math.floor(self.screenWidth * 0.5)
    width = math.max(width, getTextManager():MeasureStringX(UIFont.Medium, self.title) + padding * 2)
    local controls = {self.autoButton, self.manualButton, self.unverifiedButton,
        self.keepButton, self.disableButton, self.closeButton}
    local widths = {}
    for _, control in ipairs(controls) do
        if control:getIsVisible() then
            widths[control] = getTextManager():MeasureStringX(UIFont.Small, control.title) + 24
            width = math.max(width, widths[control] + padding * 2)
        end
    end
    width = math.min(width, self.screenWidth - 40)
    local innerWidth = width - padding * 2
    local halfWidth = math.floor((innerWidth - gap) / 2)
    local rows = {}
    local function single(control) rows[#rows + 1] = {control} end
    local function pair(left, right)
        if widths[left] <= halfWidth and widths[right] <= halfWidth then
            rows[#rows + 1] = {left, right}
        else
            single(left)
            single(right)
        end
    end
    pair(self.autoButton, self.manualButton)
    if self.unverifiedButton:getIsVisible() then single(self.unverifiedButton) end
    if self.keepButton:getIsVisible() then
        if self.updateMode then pair(self.keepButton, self.disableButton)
        else single(self.keepButton); single(self.disableButton) end
    else
        single(self.disableButton)
    end
    single(self.closeButton)
    local buttonsHeight = #rows * self.buttonHeight + (#rows - 1) * gap
    -- Retain a scrollable text viewport at small resolutions or large UI fonts.
    local minHeight = self.description.y + self.smallFontHeight * 2 + 10 + self.progressBar.height + 10 + buttonsHeight + padding
    local height = math.min(math.max(math.floor(self.screenHeight * 0.5), minHeight), self.screenHeight - 40)
    self:setWidth(width)
    self:setHeight(height)
    self:setX(math.floor((self.screenWidth - width) / 2))
    self:setY(math.floor((self.screenHeight - height) / 2))
    local buttonsY = height - padding - buttonsHeight
    for index, row in ipairs(rows) do
        for column, control in ipairs(row) do
            control:setWidth(#row == 1 and innerWidth or halfWidth)
            control:setX(padding + (column - 1) * (halfWidth + gap))
            control:setY(buttonsY + (index - 1) * (self.buttonHeight + gap))
        end
    end
    self.progressBar:setY(buttonsY - 10 - self.progressBar.height)
    self.progressBar:setWidth(innerWidth)
    local textHeight = self.progressBar.y - 10 - self.description.y
    local resized = self.description.width ~= innerWidth or self.description.height ~= textHeight
    self.description:setWidth(innerWidth)
    self.description:setHeight(textHeight)
    if resized then self.description:paginate() end
end

function SetupPanel:refresh()
    local bridge = self.bridge
    local state = bridge.state()
    local issue = bridge.issue()
    local busy = bridge.busy()
    local detail = bridge.detail()
    local canKeep = bridge.canKeepPrevious()
    local previousVersion = bridge.previousVersion()
    local compatibleVersion = bridge.compatibleVersion()
    local unverifiedVersion = bridge.unverifiedVersion()
    local signature = state .. "\n" .. issue .. "\n" .. detail .. "\n" .. tostring(self.manual) .. tostring(canKeep) .. previousVersion .. compatibleVersion .. unverifiedVersion
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
            text = text .. " <BR> " .. plainText(getText(prefix .. "CompatibleVersions", bridge.installedVersion(), compatibleVersion))
            if unverifiedVersion ~= "" then
                text = text .. " <BR> " .. plainText(getText(prefix .. "Unverified", unverifiedVersion))
            end
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
    self.unverifiedButton:setVisible(self.updateMode and unverifiedVersion ~= "")
    self.unverifiedButton:setEnable(not busy and state ~= "complete" and state ~= "disabled" and state ~= "retained" and state ~= "rollback")
    self.keepButton:setVisible(canKeep)
    self.keepButton:setEnable(canKeep and not busy)
    self.closeButton:setEnable(true)
    self.closeButton:setVisible(true)
    self:layoutControls()
end

function SetupPanel:update()
    ISPanel.update(self)
    local core = getCore()
    if self.screenWidth ~= core:getScreenWidth() or self.screenHeight ~= core:getScreenHeight() then
        self.screenWidth = core:getScreenWidth()
        self.screenHeight = core:getScreenHeight()
        self:layoutControls()
    end
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

function SetupPanel:onUnverified()
    self.bridge.startUnverifiedDownload()
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
