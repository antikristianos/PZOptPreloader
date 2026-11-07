local recoveryModule
local function ownsAotLifecycle()
    local bridge = PZOptPreloader and PZOptPreloader.AOT
    if not bridge or not bridge.ownsAotLifecycle then return false end
    local ok, owned = pcall(bridge.ownsAotLifecycle)
    return ok and owned == true
end

local function installPzfbRecoveryBoundary()
    if recoveryModule or not ownsAotLifecycle() then return end
    local mods = getActivatedMods and getActivatedMods()
    if not mods or not mods:contains("LiterallyPlayableVGCEmulation") then return end
    local ok, recovery = pcall(require, "LocalVGC/AotRecovery")
    if not ok or type(recovery) ~= "table" or type(recovery.snapshot) ~= "function" then return end
    local originalSnapshot = recovery.snapshot
    recovery.snapshot = function(...)
        local snapshot = originalSnapshot(...)
        if type(snapshot) == "table" and ownsAotLifecycle() then
            -- This recovery targets the upstream installer's different cache/launcher.
            -- Keep PZFB freshness and installation checks; preserve its journal on disk.
            snapshot.present = false
            snapshot.available = false
            snapshot.needed = false
            snapshot.journal = nil
        end
        return snapshot
    end
    recoveryModule = recovery
end

installPzfbRecoveryBoundary()
Events.OnMainMenuEnter.Add(installPzfbRecoveryBoundary)
