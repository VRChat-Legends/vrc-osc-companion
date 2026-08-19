-- VRChat Legends Companion demo script
-- Everything here runs inside the app's sealed Lua sandbox. The only things a
-- script can ever do: vrc.chatbox(text), vrc.param(name, value), vrc.wait(ms),
-- vrc.log(text), vrc.elapsed(). No files, no network, no device access.

vrc.log("demo starting")

-- A tiny spinner. Chatbox sends are auto paced so VRChat never rate limits.
local frames = { "|", "/", "-", "\\" }
for i = 1, #frames do
  vrc.chatbox("VRCL demo " .. frames[i])
end

-- Countdown with real waits.
for i = 3, 1, -1 do
  vrc.chatbox("Counting down: " .. i)
  vrc.wait(400)
end

vrc.chatbox("GG! This came from a sandboxed Lua script.\nvrchatlegends.com")

-- Uncomment to drive one of your own avatar parameters (must exist and be
-- writable on your current avatar; bools, ints 0..255, floats -1..1):
-- vrc.param("AFK", true)
-- vrc.wait(2000)
-- vrc.param("AFK", false)

vrc.log("done in " .. vrc.elapsed() .. " ms")
