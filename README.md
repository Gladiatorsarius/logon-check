# Logon Check Mod

A Fabric mod for Minecraft 1.21.11 that tracks player login activity and enforces inactivity penalties.

## Features

- **Activity Tracking**: Automatically tracks when players log in and out
- **Configurable Thresholds**: Set custom inactivity time limits via game rules
- **Automatic Enforcement**: Players who exceed the inactivity threshold are killed and banned on their next login
- **Persistent Storage**: Player activity data is saved to JSON and persists across server restarts
- **Toggle System**: Enable or disable the feature via game rule without uninstalling the mod

## Game Rules

The mod adds two custom game rules that can be configured in-game:

### `enableLogonCheck` (Boolean)
- **Default**: `false` (disabled)
- **Description**: Enables or disables the logon activity check system
- **Usage**: `/gamerule enableLogonCheck true|false`

When disabled, the mod still tracks player login times but doesn't enforce any penalties.

### `inactivityHours` (Integer)
- **Default**: `168` hours (7 days)
- **Range**: 1 - 8760 hours (1 hour to 1 year)
- **Description**: Maximum time a player can be inactive before being penalized
- **Usage**: `/gamerule inactivityHours <hours>`

## How It Works

1. **Login Tracking**: When a player logs in or logs out, their last login timestamp is recorded
2. **Inactivity Check**: On each login, if the system is enabled, the mod checks:
   - Has the player been inactive longer than the configured threshold?
   - If yes: The player is immediately killed and banned
   - If no: The player logs in normally and their timestamp is updated
3. **Data Persistence**: All login timestamps are saved to `config/logon-check-data.json`

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

## Examples

### Example 1: Weekly Activity Requirement
```
/gamerule enableLogonCheck true
/gamerule inactivityHours 168
```
Players must log in at least once every 7 days or face penalties.

### Example 2: Monthly Activity Requirement
```
/gamerule enableLogonCheck true
/gamerule inactivityHours 720
```
Players must log in at least once every 30 days.

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

Each entry maps a player's UUID to their last login timestamp (in milliseconds since epoch).

## Logs

The mod logs all activity to the server console:

- **INFO**: Normal login events with time since last login
- **WARN**: When a player is detected as inactive and penalties are applied
- **DEBUG**: Detailed timestamp updates on login/logout

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
- **LogonCheckGameRules**: Defines and registers custom game rules
- **PlayerActivityManager**: Manages player activity data and persistence
- **PlayerLoginMixin**: Intercepts player login events to check activity
- **PlayerDisconnectMixin**: Updates activity timestamps on logout

### Data Format
- Timestamps are stored as milliseconds since Unix epoch (January 1, 1970)
- Data is serialized to JSON using Gson
- Thread-safe ConcurrentHashMap used for in-memory storage

### First-Time Players
New players who have never logged in before are not considered inactive. The system only enforces penalties on returning players who exceed the threshold.

## License

CC0-1.0

## Support

For issues, questions, or contributions, visit the [GitHub repository](https://github.com/Gladiatorsarius/Swordsmp-Mods).
