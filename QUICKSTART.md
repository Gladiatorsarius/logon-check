# Logon Check - Quick Start Guide

## Installation

1. Download `logon-check-1.0.1.jar` from the releases
2. Place it in your server's `mods` folder
3. Make sure you have Fabric Loader 0.18.4+ and Fabric API 0.141.3+
4. Start your server

## Quick Setup

### Step 1: Enable the System
```
/gamerule logon-check:enable_logon_check true
```

### Step 2: Set Your Inactivity Threshold
Choose one based on your server's activity requirements:

**Daily Activity** (24 hours)
```
/gamerule logon-check:inactivity_hours 24
```

**Weekly Activity** (7 days) - Recommended Default
```
/gamerule logon-check:inactivity_hours 168
```

**Bi-Weekly Activity** (14 days)
```
/gamerule logon-check:inactivity_hours 336
```

**Monthly Activity** (30 days)
```
/gamerule logon-check:inactivity_hours 720
```

### Step 3: Set Minimum Session Time (Optional)
Configure how long players must stay online for a session to count:

**15 Minutes**
```
/gamerule logon-check:minimum_session_minutes 15
```

**30 Minutes** - Default
```
/gamerule logon-check:minimum_session_minutes 30
```

**60 Minutes** (1 hour)
```
/gamerule logon-check:minimum_session_minutes 60
```

## Checking Status

To see current configuration:
```
/gamerule logon-check:enable_logon_check
/gamerule logon-check:inactivity_hours
/gamerule logon-check:minimum_session_minutes
```

## What Happens?

**When a player logs in:**
- ✅ **Active Player**: Logs in normally, session tracking starts
- ❌ **Inactive Player**: Killed immediately and banned with reason

**While a player is online:**
- 🔄 **Real-Time Check**: Every 60 seconds, the server checks if session has reached minimum time
- ✅ **Minimum Reached**: Activity timestamp updated immediately (even if still online!)
- Session counts even if server crashes or connection drops after minimum time is reached

**When a player logs out:**
- Session tracking data is cleaned up
- Already counted sessions remain in the activity log

The ban message tells them:
- How long they were inactive
- What the limit was
- That they need to appeal or contact an admin

## Important Notes

⚠️ **First-Time Players**: New players are never penalized (no previous activity to compare)

⚠️ **Real-Time Validation**: Activity is recorded as soon as the minimum session time is reached, not waiting for disconnect. This protects against server crashes or connection issues.

⚠️ **Session Requirements**: Players must stay online for the minimum session time (default 30 min) for their visit to count as activity. Just logging in briefly won't reset the inactivity timer.

⚠️ **Data Persistence**: Activity times are saved in `config/logon-check-data.json` immediately when sessions are validated

⚠️ **Testing**: Start with the system disabled and higher hours while testing:
```
/gamerule logon-check:enable_logon_check false
/gamerule logon-check:inactivity_hours 720
/gamerule logon-check:minimum_session_minutes 30
```

⚠️ **Operators**: Server ops can use `/pardon <player>` to unban players if needed

## Logs

Check your server logs for:
```
[logon-check] Player <name> logged in after X.X hours (threshold: Y hours)
[logon-check] Session for player <uuid> reached X.X minutes (minimum: Y min) - counted as activity
[logon-check] Player <name> disconnected after X.X minutes - session duration met requirement
[logon-check] Player <name> disconnected after X.X minutes - session too short (minimum: Y min)
[logon-check] Player <name> has been inactive for X.X hours - enforcing punishment
```

The real-time validation message appears when a player's session reaches the minimum time, even if they're still online.

## Disabling

To turn off enforcement but keep tracking:
```
/gamerule logon-check:enable_logon_check false
```

To completely remove the mod, stop the server and delete `logon-check-1.0.1.jar` from the mods folder.

## Common Use Cases

### Seasonal Server with Minimum Engagement
Set to 30 days but require meaningful sessions:
```
/gamerule logon-check:enable_logon_check true
/gamerule logon-check:inactivity_hours 720
/gamerule logon-check:minimum_session_minutes 60
```

### Active Community with Short Sessions OK
Require weekly activity with 15-minute minimum:
```
/gamerule logon-check:enable_logon_check true
/gamerule logon-check:inactivity_hours 168
/gamerule logon-check:minimum_session_minutes 15
```

### Hardcore SMP with Serious Commitment
Monthly check-ins but must stay 2 hours:
```
/gamerule logon-check:enable_logon_check true
/gamerule logon-check:inactivity_hours 720
/gamerule logon-check:minimum_session_minutes 120
```

## Troubleshooting

**Q: Players are getting banned immediately!**
A: Check your `logon-check:inactivity_hours` setting. You may have set it too low, or your data file has old timestamps. Increase the hours or delete `config/logon-check-data.json` to reset.

**Q: The mod isn't doing anything**
A: Make sure `logon-check:enable_logon_check` is set to `true`. The mod tracks sessions even when disabled, but won't enforce penalties.

**Q: Players complain their sessions don't count**
A: Check the `logon-check:minimum_session_minutes` setting. Players must stay online for the full minimum time. Check server logs to see actual session durations.

**Q: Can I exempt certain players?**
A: Not currently. All players are subject to the same rules. Consider using permission-based bypass in future versions.

**Q: Where is the data stored?**
A: In `config/logon-check-data.json` - you can delete this file to reset all player data.

## Support

For bugs or feature requests, visit: https://github.com/Gladiatorsarius/Swordsmp-Mods
