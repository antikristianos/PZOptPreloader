-- Own post-load filter. Original menu implementation remains in the separate PZOpt installation.
local function removedKey(value)
    if type(value) ~= "string" then return false end
    local key = string.lower(value)
    return key:find("^pixellight") or key:find("^voxellight") or key:find("^godrays")
        or key:find("^ppl") or key:find("^devppl") or key:find("^devvoxellight")
        or key:find("^devpixellight") or key:find("^devgodrays")
end
local survivingSections = {}
local function hasSection(prefix)
    for title in pairs(survivingSections) do
        if title:sub(1, #prefix) == prefix then return true end
    end
    return false
end
local function filterData(data, visited, sectionNames)
    if type(data) ~= "table" or visited[data] then return end
    visited[data] = true
    for index = #data, 1, -1 do
        local value = data[index]
        if removedKey(value) or sectionNames and type(value) == "string" and not hasSection(value)
                or type(value) == "table" and removedKey(value.key) then
            table.remove(data, index)
        else
            filterData(value, visited, false)
            if type(value) == "table" then
                local empty = value.entries and #value.entries == 0
                    or (value.keys or value.sections) and #(value.keys or {}) == 0 and #(value.sections or {}) == 0
                    or value.subs and #value.subs == 0 or value.cats and #value.cats == 0
                if empty then
                    table.remove(data, index)
                end
            end
        end
    end
    for key, value in pairs(data) do
        if removedKey(key) then data[key] = nil
        elseif type(key) ~= "number" then filterData(value, visited, key == "sections") end
    end
end
if PzoptOptionSections then
    local pages = PzoptOptionSections()
    filterData(pages, {}, false)
    for _, sections in ipairs(pages) do
        for _, section in ipairs(sections) do
            if section.title then survivingSections[section.title] = true end
        end
    end
end
if PzoptSettingsLayout then
    filterData(PzoptSettingsLayout, {}, false)
    -- Titles are taken from surviving installed data; no excluded feature remains advertised here.
    for _, group in ipairs(PzoptSettingsLayout.groups or {}) do
        for _, category in ipairs(group.cats or {}) do
            if category.id == "light" then
                local titles = {}
                for _, subcategory in ipairs(category.subs or {}) do titles[#titles + 1] = subcategory.title end
                category.blurb = table.concat(titles, ", ")
            end
        end
    end
end
if PzoptSettings then
    filterData(PzoptSettings, {}, false)
    local originalClip = PzoptSettings.clipOf
    if originalClip then
        PzoptSettings.clipOf = function(...)
            local clip = originalClip(...)
            if clip == "torch" or clip == "voxel" or string.find(clip or "", "^godrays") then return "drive" end
            return clip
        end
    end
    for _, master in ipairs(PzoptSettings.MASTERS or {}) do
        if master.restartKeys then filterData(master.restartKeys, {}, false) end
        if master.tip then
            master.tip = master.tip:gsub("per%-pixel lighting, ", ""):gsub(", god rays", "")
        end
    end
end