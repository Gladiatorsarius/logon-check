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
2. **Session Validation**: When a player logs out, the mod checks:
   - Did the player stay online for at least the minimum session time?
   - If yes: Their last activity timestamp is updated
   - If no: The session doesn't count, and their inactivity timer continues
3. **Inactivity Check**: On each login, if the system is enabled, the mod checks:
   - Has the player been inactive longer than the configured threshold?
   - If yes: The player is immediately killed and banned
   - If no: The player logs in normally and starts a new session
4. **Data Persistence**: All activity timestamps are saved to `config/logon-check-data.json`

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

Each entry maps a player's UUID to their last successful activity timestamp (in milliseconds since epoch). Sessions are tracked in memory and only saved to this file if they meet the minimum session time requirement.

## Logs

The mod logs all activity to the server console:

- **INFO**: Session durations and whether they counted as activity
- **INFO**: Login events with time since last activity
- **WARN**: When a player is detected as inactive and penalties are applied
- **DEBUG**: Session start tracking events

**Example log messages:**
```
[INFO] Player Steve logged in after 156.2 hours (threshold: 168 hours)
[INFO] Player Alex disconnected after 45.3 minutes - session counted as activity
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
- **PlayerActivityManager**: Manages player activity data, session tracking, and persistence
- **PlayerLoginMixin**: Intercepts player login events to check inactivity and start session tracking
- **PlayerDisconnectMixin**: Ends sessions and validates minimum session time before updating activity

### Session Tracking
- Session start times are tracked in memory when players log in
- On disconnect, session duration is calculated and compared to minimum threshold
- Only sessions meeting the minimum duration update the persistent activity timestamp
- Session tracking is separate from persistent data to allow real-time validation

### Data Format
- Activity timestamps are stored as milliseconds since Unix epoch (January 1, 1970)
- Data is serialized to JSON using Gson
- Thread-safe ConcurrentHashMap used for both activity timestamps and session tracking
- Session data is ephemeral (memory-only) while activity data is persistent

### First-Time Players
New players who have never logged in before are not considered inactive. The system only enforces penalties on returning players who exceed the threshold.

## License

CC0-1.0

## Support

For issues, questions, or contributions, visit the [GitHub repository](https://github.com/Gladiatorsarius/Swordsmp-Mods).
