# Logon Check Mod

A Fabric mod for Minecraft 1.21.11 that tracks player login activity and enforces inactivity penalties.

## Features

- **Activity Tracking**: Automatically tracks when players log in and out
- **Session Duration Requirements**: Players must stay online for a minimum time for sessions to count as activity
- **Configurable Thresholds**: Set custom inactivity time limits and minimum session times via game rules
- **Automatic Enforcement**: Players who exceed the inactivity threshold are killed and banned on their next login
- **Persistent Storage**: Player activity data is saved to JSON and persists across server restarts
- **Toggle System**: Enable or disable the feature via game rule without uninstalling the mod

## Game Rules

The mod adds three custom game rules that can be configured in-game:

### `enableLogonCheck` (Boolean)
- **Default**: `false` (disabled)
- **Description**: Enables or disables the logon activity check system
- **Usage**: `/gamerule enableLogonCheck true|false`

When disabled, the mod still tracks session durations but doesn't enforce inactivity penalties.

### `inactivityHours` (Integer)
- **Default**: `168` hours (7 days)
- **Range**: 1 - 8760 hours (1 hour to 1 year)
- **Description**: Maximum time a player can be inactive before being penalized
- **Usage**: `/gamerule inactivityHours <hours>`

### `minimumSessionMinutes` (Integer)
- **Default**: `30` minutes
- **Range**: 1 - 1440 minutes (1 minute to 24 hours)
- **Description**: Minimum time a player must stay online for the session to count as activity
- **Usage**: `/gamerule minimumSessionMinutes <minutes>`

**Important**: Sessions shorter than this threshold will not reset the inactivity timer. This prevents players from just logging in briefly to avoid penalties.

## How It Works

1. **Session Tracking**: When a player logs in, the mod starts tracking their session time
2. **Real-Time Validation**: Every 60 seconds, the server checks all active sessions:
   - Has the player been online for at least the minimum session time?
   - If yes: Their last activity timestamp is updated immediately (even if they're still online)
   - If no: The check continues on the next tick cycle
3. **Session Cleanup**: When a player logs out, the mod cleans up session tracking data
4. **Inactivity Check**: On each login, if the system is enabled, the mod checks:
   - Has the player been inactive longer than the configured threshold?
   - If yes: The player is immediately killed and banned
   - If no: The player logs in normally and starts a new session
5. **Data Persistence**: All activity timestamps are saved to `config/logon-check-data.json` as soon as sessions are validated

**Key Benefit**: Activity is recorded as soon as the minimum time is reached, not waiting for disconnect. This means sessions count even if the server crashes or the player's connection drops after reaching the minimum time.

## Installation

1. Ensure you have Fabric Loader 0.18.4+ installed
2. Ensure you have Fabric API 0.141.3+ for Minecraft 1.21.11
3. Place `logon-check-1.0.0.jar` in your server's `mods` folder
4. Start the server

## Configuration

### Enable the System
```
/gamerule enableLogonCheck true
```

### Set Inactivity Threshold
```
# 24 hours (1 day)
/gamerule inactivityHours 24

# 168 hours (7 days) - default
/gamerule inactivityHours 168

# 720 hours (30 days)
/gamerule inactivityHours 720
```

### Set Minimum Session Time
```
# 15 minutes
/gamerule minimumSessionMinutes 15

# 30 minutes - default
/gamerule minimumSessionMinutes 30

# 60 minutes (1 hour)
/gamerule minimumSessionMinutes 60
```

## Examples

### Example 1: Weekly Activity with 30-Minute Minimum
```
/gamerule enableLogonCheck true
/gamerule inactivityHours 168
/gamerule minimumSessionMinutes 30
```
Players must log in at least once every 7 days and stay for at least 30 minutes.

### Example 2: Monthly Activity with 1-Hour Minimum
```
/gamerule enableLogonCheck true
/gamerule inactivityHours 720
/gamerule minimumSessionMinutes 60
```
Players must log in at least once every 30 days and stay for at least 1 hour.

### Example 3: Disable the System
```
/gamerule enableLogonCheck false
```
The mod continues tracking but doesn't enforce penalties.

## Penalty Details

When a player exceeds the inactivity threshold:
1. **Killed**: The player is immediately killed upon login
2. **Banned**: The player is banned from the server with a message explaining the reason
3. **Data Cleared**: Their activity record is removed from the system

The ban message includes:
- How long they were inactive
- What the maximum allowed inactivity was

## Data Storage

Player activity data is stored in `config/logon-check-data.json`:

```json
{
  "uuid-here": 1738849200000,
  "another-uuid": 1738935600000
}
```

Each entry maps a player's UUID to their last successful activity timestamp (in milliseconds since epoch). Sessions are tracked in memory and validated in real-time every 60 seconds. Once validated, they're immediately saved to this file.

## Logs

The mod logs all activity to the server console:

- **INFO**: Real-time session validation when minimum time is reached
- **INFO**: Session durations and status on disconnect
- **INFO**: Login events with time since last activity
- **WARN**: When a player is detected as inactive and penalties are applied
- **DEBUG**: Session start tracking events

**Example log messages:**
```
[INFO] Player Steve logged in after 156.2 hours (threshold: 168 hours)
[INFO] Session for player <uuid> reached 30.1 minutes (minimum: 30 min) - counted as activity
[INFO] Player Alex disconnected after 45.3 minutes - session duration met requirement
[INFO] Player Bob disconnected after 12.7 minutes - session too short (minimum: 30 min)
[WARN] Player Charlie has been inactive for 185.4 hours (threshold: 168 hours) - enforcing punishment
```

## Requirements

- **Minecraft**: 1.21.11
- **Fabric Loader**: 0.18.4 or newer
- **Fabric API**: 0.141.3+1.21.11 or newer
- **Java**: 21 or newer

## Building from Source

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/logon-check-1.0.0.jar`

## Technical Details

### Architecture
- **LogonCheck**: Main mod initialization and server lifecycle management
- **LogonCheckGameRules**: Defines and registers custom game rules (boolean and integer)
- **PlayerActivityManager**: Manages player activity data, session tracking, and real-time validation
- **PlayerLoginMixin**: Intercepts player login events to check inactivity and start session tracking
- **PlayerDisconnectMixin**: Cleans up session tracking data on disconnect
- **ServerTickMixin**: Periodically checks active sessions every 60 seconds for real-time validation

### Session Tracking
- Session start times are tracked in memory when players log in
- Every 60 seconds (1200 ticks), the server checks all active sessions
- When a session reaches the minimum duration, activity timestamp is immediately updated
- Sessions are marked as "counted" to prevent double-counting
- On disconnect, session tracking data is cleaned up
- Real-time validation ensures activity is recorded even if server crashes after minimum time

### Data Format
- Activity timestamps are stored as milliseconds since Unix epoch (January 1, 1970)
- Data is serialized to JSON using Gson
- Thread-safe ConcurrentHashMap used for activity timestamps, session tracking, and session counting flags
- Session data (start times and counted flags) is ephemeral (memory-only)
- Activity timestamps are persistent and immediately saved when sessions are validated

### First-Time Players
New players who have never logged in before are not considered inactive. The system only enforces penalties on returning players who exceed the threshold.

## License

CC0-1.0

## Support

For issues, questions, or contributions, visit the [GitHub repository](https://github.com/Gladiatorsarius/Swordsmp-Mods).
